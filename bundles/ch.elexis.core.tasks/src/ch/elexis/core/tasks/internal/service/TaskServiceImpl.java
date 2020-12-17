package ch.elexis.core.tasks.internal.service;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.component.annotations.ReferencePolicyOption;
import org.quartz.SchedulerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.elexis.core.model.IUser;
import ch.elexis.core.model.message.MessageCode;
import ch.elexis.core.model.message.TransientMessage;
import ch.elexis.core.model.tasks.IIdentifiedRunnable;
import ch.elexis.core.model.tasks.IIdentifiedRunnable.ReturnParameter;
import ch.elexis.core.model.tasks.IIdentifiedRunnableFactory;
import ch.elexis.core.model.tasks.TaskException;
import ch.elexis.core.services.IContextService;
import ch.elexis.core.services.IMessageService;
import ch.elexis.core.services.IModelService;
import ch.elexis.core.services.IQuery;
import ch.elexis.core.services.IQuery.COMPARATOR;
import ch.elexis.core.services.IQuery.ORDER;
import ch.elexis.core.tasks.internal.service.fs.WatchServiceHolder;
import ch.elexis.core.tasks.internal.service.quartz.QuartzExecutor;
import ch.elexis.core.tasks.internal.service.sysevents.SysEventWatcher;
import ch.elexis.core.tasks.model.ITask;
import ch.elexis.core.tasks.model.ITaskDescriptor;
import ch.elexis.core.tasks.model.ITaskService;
import ch.elexis.core.tasks.model.ModelPackage;
import ch.elexis.core.tasks.model.OwnerTaskNotification;
import ch.elexis.core.tasks.model.TaskState;
import ch.elexis.core.tasks.model.TaskTriggerType;

@Component(immediate = true)
public class TaskServiceImpl implements ITaskService {
	
	private Logger logger = LoggerFactory.getLogger(getClass());
	
	private IModelService taskModelService;
	private TaskServiceUtil util;
	private ExecutorService parallelExecutorService;
	private Map<String, ExecutorService> perRunnableSingletonExecutorService;
	private QuartzExecutor quartzExecutor;
	private WatchServiceHolder watchServiceHolder;
	private SysEventWatcher sysEventWatcher;
	private List<ITask> triggeredTasks;
	
	//TODO OtherTaskService -> this
	
	//private List<ITaskDescriptor> incurredTasks;
	
	@Reference
	private IContextService contextService;
	
	@Reference
	private IMessageService messageService;
	
	@Reference(target = "(" + IModelService.SERVICEMODELNAME + "=ch.elexis.core.tasks.model)")
	private void setModelService(IModelService modelService){
		taskModelService = modelService;
	}
	
	/**
	 * do not execute these instances, they are used for documentation listing only
	 */
	private List<IIdentifiedRunnable> identifiedRunnables;
	
	private Map<String, IIdentifiedRunnableFactory> runnableIdToFactoryMap;
	
	@Reference(cardinality = ReferenceCardinality.MULTIPLE, policy = ReferencePolicy.DYNAMIC, policyOption = ReferencePolicyOption.GREEDY, bind = "bindRunnableWithContextFactory", unbind = "unbindRunnableWithContextFactory")
	private volatile List<IIdentifiedRunnableFactory> runnableWithContextFactories;
	
	protected void bindRunnableWithContextFactory(
		IIdentifiedRunnableFactory runnableWithContextFactory){
		if (runnableWithContextFactories == null) {
			runnableWithContextFactories = new ArrayList<>();
		}
		logger.info("Binding " + runnableWithContextFactory.getClass().getName());
		runnableWithContextFactories.add(runnableWithContextFactory);
		
		try {
			runnableWithContextFactory.initialize(this);
			
			List<IIdentifiedRunnable> providedRunnables =
				runnableWithContextFactory.getProvidedRunnables();
			for (IIdentifiedRunnable iIdentifiedRunnable : providedRunnables) {
				runnableIdToFactoryMap.put(iIdentifiedRunnable.getId(), runnableWithContextFactory);
				identifiedRunnables.add(iIdentifiedRunnable);
				
				loadIncurredForRunnable(iIdentifiedRunnable);
			}
			
		} catch (Exception e) {
			logger.warn("Error binding [{}], skipping.",
				runnableWithContextFactory.getClass().getName(), e);
			return;
		}
		
	}
	
	protected void unbindRunnableWithContextFactory(
		IIdentifiedRunnableFactory runnableWithContextFactory){
		runnableWithContextFactories.remove(runnableWithContextFactory);
		List<IIdentifiedRunnable> providedRunnables =
			runnableWithContextFactory.getProvidedRunnables();
		for (IIdentifiedRunnable iIdentifiedRunnable : providedRunnables) {
			runnableIdToFactoryMap.remove(iIdentifiedRunnable.getId());
			identifiedRunnables.remove(iIdentifiedRunnable);
			
			unloadIncurredForRunnable(iIdentifiedRunnable);
		}
	}
	
	public TaskServiceImpl(){
		identifiedRunnables = Collections.synchronizedList(new ArrayList<>());
		runnableIdToFactoryMap = Collections.synchronizedMap(new HashMap<>());
		triggeredTasks = Collections.synchronizedList(new ArrayList<>());
		parallelExecutorService = Executors.newCachedThreadPool();
		perRunnableSingletonExecutorService = new HashMap<>();
		quartzExecutor = new QuartzExecutor();
		sysEventWatcher = new SysEventWatcher();
		util = new TaskServiceUtil();
	}
	
	@Activate
	private void activateComponent(){
		try {
			quartzExecutor.start();
		} catch (SchedulerException e) {
			logger.warn("Error starting quartz scheduler", e);
		}
		
		watchServiceHolder = new WatchServiceHolder(this);
		if (watchServiceHolder.triggerIsAvailable()) {
			watchServiceHolder.startPolling();
		}
	}
	
	@Deactivate
	private void deactivateComponent(){
		
		List<ITask> runningTasks = getRunningTasks();
		long start = System.currentTimeMillis();
		while (!runningTasks.isEmpty() && (System.currentTimeMillis() - start < 30000)) {
			for (ITask task : runningTasks) {
				IProgressMonitor progressMonitor = task.getProgressMonitor();
				if (!progressMonitor.isCanceled()) {
					logger.info("Canceling " + task.getLabel());
					task.getProgressMonitor().setCanceled(true);
				}
			}
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {}
			runningTasks = getRunningTasks();
			logger.info("Waiting max 30 seconds for tasks to gracefully stop");
		}
		
		getRunningTasks()
			.forEach(task -> logger.warn("Could not gracefully stop task " + task.getLabel()));
		
		try {
			quartzExecutor.shutdown();
			parallelExecutorService.shutdown();
			perRunnableSingletonExecutorService.forEach((c, e) -> e.shutdown());
		} catch (SchedulerException e) {
			logger.warn("Error stopping scheduler", e);
		}
		watchServiceHolder.stopPolling();
	}
	
	//TODO: check - are there any TDs we are responsible for, and yet not loaded?
	/**
	 * Load a task descriptors we are responsible for and start (incur) them
	 */
	private void loadIncurredForRunnable(IIdentifiedRunnable identifiedRunnable){
		List<ITaskDescriptor> taskDescriptors =
			util.loadForIdentifiedRunnable(identifiedRunnable, taskModelService, contextService);
		for (ITaskDescriptor iTaskDescriptor : taskDescriptors) {
			try {
				logger.info("incurring task descriptor [{}] reference id [{}]",
					iTaskDescriptor.getId(), iTaskDescriptor.getReferenceId());
				incur(iTaskDescriptor);
			} catch (TaskException e) {
				logger.warn("Can not incur taskdescriptor [{}]", iTaskDescriptor.getId(), e);
			}
		}
	}
	
	/**
	 * Unload (release) task descriptors we are responsible for
	 */
	private void unloadIncurredForRunnable(IIdentifiedRunnable identifiedRunnable){
		List<ITaskDescriptor> taskDescriptors =
			util.loadForIdentifiedRunnable(identifiedRunnable, taskModelService, contextService);
		for (ITaskDescriptor iTaskDescriptor : taskDescriptors) {
			try {
				logger.info("releasing task descriptor [{}] reference id [{}]",
					iTaskDescriptor.getId(), iTaskDescriptor.getReferenceId());
				release(iTaskDescriptor);
			} catch (TaskException e) {
				logger.warn("Can not release taskdescriptor [{}]", iTaskDescriptor.getId(), e);
			}
		}
	}
	
	/**
	 * Become responsible for executing this task when required. This includes asserting that the
	 * necessary execution events are generated.
	 * 
	 * @param task
	 * @throws TaskException
	 */
	private void incur(ITaskDescriptor taskDescriptor) throws TaskException{
		if (TaskTriggerType.FILESYSTEM_CHANGE == taskDescriptor.getTriggerType()) {
			watchServiceHolder.incur(taskDescriptor);
		} else if (TaskTriggerType.CRON == taskDescriptor.getTriggerType()) {
			quartzExecutor.incur(this, taskDescriptor);
		} else if (TaskTriggerType.MANUAL == taskDescriptor.getTriggerType()) {
			// nothing to be done
		} else if (TaskTriggerType.OTHER_TASK == taskDescriptor.getTriggerType()) {
			// nothing to be done
		} else if (TaskTriggerType.SYSTEM_EVENT == taskDescriptor.getTriggerType()) {
			sysEventWatcher.incur(taskDescriptor);
		} else {
			throw new TaskException(TaskException.TRIGGER_NOT_AVAILABLE,
				"Trigger type not yet implemented [" + taskDescriptor.getTriggerType() + "]");
		}
	}
	
	/**
	 * Release responsibility for executing this task when required.
	 * 
	 * @param taskDescriptor
	 * @throws TaskException
	 */
	private void release(ITaskDescriptor taskDescriptor) throws TaskException{
		if (TaskTriggerType.FILESYSTEM_CHANGE == taskDescriptor.getTriggerType()) {
			watchServiceHolder.release(taskDescriptor);
		} else if (TaskTriggerType.CRON == taskDescriptor.getTriggerType()) {
			quartzExecutor.release(taskDescriptor);
		} else if (TaskTriggerType.SYSTEM_EVENT == taskDescriptor.getTriggerType()) {
			sysEventWatcher.release(taskDescriptor);
		}
	}
	
	@Override
	public ITaskDescriptor createTaskDescriptor(IIdentifiedRunnable identifiedRunnable)
		throws TaskException{
		
		if (identifiedRunnable == null) {
			throw new TaskException(TaskException.PARAMETERS_MISSING);
		}
		
		ITaskDescriptor taskDescriptor = taskModelService.create(ITaskDescriptor.class);
		taskDescriptor.setIdentifiedRunnableId(identifiedRunnable.getId());
		taskDescriptor.setRunContext(identifiedRunnable.getDefaultRunContext());
		
		String stationIdentifier = contextService.getRootContext().getStationIdentifier();
		taskDescriptor.setRunner(StringUtils.abbreviate(stationIdentifier, 64));
		
		contextService.getActiveUser().ifPresent(u -> taskDescriptor.setOwner(u));
		
		saveTaskDescriptor(taskDescriptor);
		
		return taskDescriptor;
	}
	
	@Override
	public boolean removeTaskDescriptor(ITaskDescriptor taskDescriptor) throws TaskException{
		
		if (taskDescriptor == null) {
			throw new TaskException(TaskException.PARAMETERS_MISSING);
		}
		
		setActive(taskDescriptor, false);
		
		IQuery<ITask> taskQuery = taskModelService.getQuery(ITask.class, true);
		taskQuery.and(ModelPackage.Literals.ITASK__TASK_DESCRIPTOR, COMPARATOR.EQUALS,
			taskDescriptor);
		List<ITask> execute = taskQuery.execute();
		execute.stream().forEach(task -> taskModelService.remove(task));
		
		return taskModelService.remove(taskDescriptor);
	}
	
	void notify(ITask task){
		if (task.isFinished()) {
			// TODO tasks that are triggered by this task
			
			triggeredTasks.remove(task);
			
			ITaskDescriptor taskDescriptor = task.getTaskDescriptor();
			OwnerTaskNotification ownerNotification = taskDescriptor.getOwnerNotification();
			IUser owner = taskDescriptor.getOwner();
			
			TaskState state = task.getState();
			if (OwnerTaskNotification.WHEN_FINISHED == ownerNotification
				|| (OwnerTaskNotification.WHEN_FINISHED_FAILED == ownerNotification
					&& (TaskState.FAILED == state || TaskState.COMPLETED_WARN == state))) {
				
				if (owner != null) {
					sendMessageToOwner(task, owner, state);
				} else {
					logger.warn("[{}] requested owner notification, but owner is null",
						task.getTaskDescriptor().getId());
				}
				
			}
		}
	}
	
	private void sendMessageToOwner(ITask task, IUser owner, TaskState state){
		TransientMessage message = messageService.prepare(
			"Task-Service@" + contextService.getRootContext().getStationIdentifier(),
			IMessageService.INTERNAL_MESSAGE_URI_SCHEME + ":" + owner.getId());
		message.addMessageCode(MessageCode.Key.SenderSubId, "tasks.taskservice");
		message.setSenderAcceptsAnswer(false);
		
		String resultText;
		if (TaskState.FAILED == state) {
			resultText =
				(String) task.getResult().get(ReturnParameter.FAILED_TASK_EXCEPTION_MESSAGE);
			message.addMessageCode(MessageCode.Key.Severity, MessageCode.Value.Severity_WARN);
		} else {
			// TODO handle result type
			resultText = (String) task.getResult().get(ReturnParameter.RESULT_DATA);
			message.addMessageCode(MessageCode.Key.Severity, MessageCode.Value.Severity_INFO);
		}
		
		StringBuilder sb = new StringBuilder();
		sb.append(task.getLabel());
		if (StringUtils.isNotBlank(resultText)) {
			sb.append("\n" + resultText);
		}
		message.setMessageText(sb.toString());
		
		IStatus status = messageService.send(message);
		if (!status.isOK()) {
			logger.warn("Could not send message to owner [{}]", status.getMessage());
		}
	}
	
	@Override
	public ITask trigger(ITaskDescriptor taskDescriptor, IProgressMonitor progressMonitor,
		TaskTriggerType triggerType, Map<String, String> runContext) throws TaskException{
		
		logger.info("[{}] trigger taskDesc [{}/{}] runContext [{}]", triggerType,
			taskDescriptor.getId(), taskDescriptor.getReferenceId(), runContext);
		
		Task task = new Task(taskDescriptor, triggerType, progressMonitor, runContext);
		
		// TODO test if all runContext parameters are satisfied, else reject execution
		task.setState(TaskState.QUEUED);
		
		String identifiedRunnableId = taskDescriptor.getIdentifiedRunnableId();
		boolean singletonRunnable = instantiateRunnableById(identifiedRunnableId).isSingleton();
		
		try {
			if (singletonRunnable || taskDescriptor.isSingleton()) {
				// singleton tasks/runnables must not run in multiple instances in parallel
				// hence we hold a separate thread for each of them
				if (!perRunnableSingletonExecutorService.containsKey(identifiedRunnableId)) {
					ExecutorService executorService = Executors.newSingleThreadExecutor();
					perRunnableSingletonExecutorService.put(identifiedRunnableId, executorService);
				}
				ExecutorService executorService =
					perRunnableSingletonExecutorService.get(identifiedRunnableId);
				executorService.execute((Runnable) task);
				
			} else {
				parallelExecutorService.execute((Runnable) task);
			}
			triggeredTasks.add(task);
		} catch (RejectedExecutionException re) {
			task.setState(TaskState.CANCELLED);
			// TODO triggering failed, where to show?
			throw new TaskException(TaskException.EXECUTION_REJECTED, re);
		}
		return task;
	}
	
	@Override
	public ITask trigger(String taskDescriptorReferenceId, IProgressMonitor progressMonitor,
		TaskTriggerType triggerType, Map<String, String> runContext) throws TaskException{
		
		IQuery<ITaskDescriptor> query = taskModelService.getQuery(ITaskDescriptor.class);
		query.and(ModelPackage.Literals.ITASK_DESCRIPTOR__REFERENCE_ID, COMPARATOR.EQUALS,
			taskDescriptorReferenceId);
		Optional<ITaskDescriptor> taskDescriptor = query.executeSingleResult();
		if (taskDescriptor.isPresent()) {
			return trigger(taskDescriptor.get(), progressMonitor, triggerType, runContext);
		}
		throw new TaskException(TaskException.EXECUTION_REJECTED,
			"Could not find task descriptor reference id [" + taskDescriptorReferenceId + "]");
	}
	
	@Override
	public IIdentifiedRunnable instantiateRunnableById(String runnableId) throws TaskException{
		if (runnableId == null || runnableId.length() == 0) {
			throw new TaskException(TaskException.RWC_INVALID_ID);
		}
		
		IIdentifiedRunnableFactory iIdentifiedRunnableFactory =
			runnableIdToFactoryMap.get(runnableId);
		if (iIdentifiedRunnableFactory != null) {
			List<IIdentifiedRunnable> providedRunnables =
				iIdentifiedRunnableFactory.getProvidedRunnables();
			for (IIdentifiedRunnable iIdentifiedRunnable : providedRunnables) {
				if (runnableId.equalsIgnoreCase(iIdentifiedRunnable.getId())) {
					return iIdentifiedRunnable;
				}
			}
		}
		
		String reason;
		if (iIdentifiedRunnableFactory == null) {
			reason = "no registered factory found";
		} else {
			reason = "runnable id not found in factory ["
				+ iIdentifiedRunnableFactory.getClass().getName() + "]";
		}
		
		throw new TaskException(TaskException.RWC_NO_INSTANCE_FOUND,
			"Could not instantiate runnable id [" + runnableId + "]: " + reason);
	}
	
	@Override
	public void saveTaskDescriptor(ITaskDescriptor taskDescriptor) throws TaskException{
		boolean save = taskModelService.save((TaskDescriptor) taskDescriptor);
		if (!save) {
			throw new TaskException(TaskException.PERSISTENCE_ERROR);
		}
	}
	
	@Override
	public void setActive(ITaskDescriptor taskDescriptor, boolean active) throws TaskException{
		
		if (taskDescriptor.isActive() == active) {
			return;
		}
		
		if (active) {
			validateTaskDescriptor(taskDescriptor);
		}
		
		taskDescriptor.setActive(active);
		saveTaskDescriptor(taskDescriptor);
		
		if (active) {
			incur(taskDescriptor);
		} else {
			release(taskDescriptor);
		}
	}
	
	/**
	 * validate if the required parameters are set, else we must not allow activating it.
	 * 
	 * @param taskDescriptor
	 */
	private void validateTaskDescriptor(ITaskDescriptor taskDescriptor) throws TaskException{
		
		IIdentifiedRunnable runnable =
			instantiateRunnableById(taskDescriptor.getIdentifiedRunnableId());
		
		if (TaskTriggerType.OTHER_TASK == taskDescriptor.getTriggerType()) {
			// we will not check activation here, as the required parameters
			// will be supplied by the other task invoking us (we don't know about the
			// supplied parameters)
			return;
		}
		
		if (TaskTriggerType.SYSTEM_EVENT == taskDescriptor.getTriggerType()) {
			// we will not check activation here, no formal required parameters
			// system event will only pass what's available
			return;
		}
		
		Set<Entry<String, Serializable>> entrySet = runnable.getDefaultRunContext().entrySet();
		for (Entry<String, Serializable> entry : entrySet) {
			if (IIdentifiedRunnable.RunContextParameter.VALUE_MISSING_REQUIRED
				.equals(entry.getValue())) {
				Serializable value = taskDescriptor.getRunContext().get(entry.getKey());
				if (value == null || IIdentifiedRunnable.RunContextParameter.VALUE_MISSING_REQUIRED
					.equals(value)) {
					throw new TaskException(TaskException.PARAMETERS_MISSING,
						"Missing required parameter [" + entry.getKey() + "]");
				}
			}
		}
		
		if (taskDescriptor.getOwner() == null) {
			throw new TaskException(TaskException.PARAMETERS_MISSING, "Missing owner");
		}
		
	}
	
	@Override
	public List<IIdentifiedRunnable> getIdentifiedRunnables(){
		return identifiedRunnables;
	}
	
	@Override
	public Optional<ITaskDescriptor> findTaskDescriptorByIdOrReferenceId(String idOrReferenceId){
		IQuery<ITaskDescriptor> query =
			taskModelService.getQuery(ITaskDescriptor.class, true, false);
		query.and(ModelPackage.Literals.ITASK_DESCRIPTOR__ID, COMPARATOR.EQUALS, idOrReferenceId);
		query.or(ModelPackage.Literals.ITASK_DESCRIPTOR__REFERENCE_ID, COMPARATOR.EQUALS,
			idOrReferenceId);
		return query.executeSingleResult();
	}
	
	@Override
	public Optional<ITask> findLatestExecution(ITaskDescriptor taskDescriptor){
		IQuery<ITask> query = taskModelService.getQuery(ITask.class);
		query.and(ModelPackage.Literals.ITASK__TASK_DESCRIPTOR, COMPARATOR.EQUALS, taskDescriptor);
		query.orderBy(ch.elexis.core.model.ModelPackage.Literals.IDENTIFIABLE__LASTUPDATE,
			ORDER.DESC);
		query.limit(1);
		List<ITask> result = query.execute();
		return result.isEmpty() ? Optional.empty() : Optional.of(result.get(0));
	}
	
	@Override
	public List<ITask> getRunningTasks(){
		return new ArrayList<ITask>(triggeredTasks);
	}
	
	@Override
	public List<ITaskDescriptor> getIncurredTasks(){
		List<ITaskDescriptor> projectedTaskDescriptors = new ArrayList<ITaskDescriptor>();
		Set<String[]> incurred = quartzExecutor.getIncurred();
		incurred.stream().forEach(i -> {
			ITaskDescriptor taskDescriptor =
				taskModelService.load(i[0], ITaskDescriptor.class).orElse(null);
			if (taskDescriptor != null) {
				taskDescriptor.getTransientData().put("cron-next-exectime", i[1]);
				projectedTaskDescriptors.add(taskDescriptor);
			}
		});
		return projectedTaskDescriptors;
	}
	
}