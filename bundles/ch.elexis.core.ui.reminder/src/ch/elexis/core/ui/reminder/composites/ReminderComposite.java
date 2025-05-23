package ch.elexis.core.ui.reminder.composites;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.core.databinding.DataBindingContext;
import org.eclipse.core.databinding.beans.typed.PojoProperties;
import org.eclipse.core.databinding.observable.value.IObservableValue;
import org.eclipse.core.databinding.observable.value.WritableValue;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.ToolBarManager;
import org.eclipse.jface.databinding.swt.ISWTObservableValue;
import org.eclipse.jface.databinding.swt.typed.WidgetProperties;
import org.eclipse.jface.databinding.viewers.IViewerObservableValue;
import org.eclipse.jface.databinding.viewers.typed.ViewerProperties;
import org.eclipse.jface.fieldassist.ContentProposal;
import org.eclipse.jface.fieldassist.ContentProposalAdapter;
import org.eclipse.jface.fieldassist.IContentProposal;
import org.eclipse.jface.fieldassist.IContentProposalListener;
import org.eclipse.jface.fieldassist.TextContentAdapter;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.ComboViewer;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.nebula.widgets.cdatetime.CDT;
import org.eclipse.nebula.widgets.cdatetime.CDateTime;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.KeyAdapter;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;

import ch.elexis.core.l10n.Messages;
import ch.elexis.core.model.IContact;
import ch.elexis.core.model.IPatient;
import ch.elexis.core.model.IReminder;
import ch.elexis.core.model.IUser;
import ch.elexis.core.model.IUserGroup;
import ch.elexis.core.model.issue.Priority;
import ch.elexis.core.model.issue.Type;
import ch.elexis.core.model.issue.Visibility;
import ch.elexis.core.services.IQuery;
import ch.elexis.core.services.IQuery.COMPARATOR;
import ch.elexis.core.services.IUserService;
import ch.elexis.core.services.holder.CoreModelServiceHolder;
import ch.elexis.core.time.TimeUtil;
import ch.elexis.core.ui.e4.fieldassist.AsyncContentProposalProvider;
import ch.elexis.core.ui.e4.util.CoreUiUtil;
import ch.elexis.core.ui.icons.Images;
import ch.elexis.core.ui.proposals.IdentifiableContentProposal;
import ch.elexis.core.ui.proposals.IdentifiableProposalProvider;
import jakarta.inject.Inject;

public class ReminderComposite extends Composite {

	private ComboViewer typeViewer;

	private Text subjectTxt;
	private Text msgText;

	private Text patSearchText;
	private Text userSearchText;
	private Text groupSearchText;

	private BooleanExpandableComposite dueExpandable;

	@Inject
	private IUserService userService;

	protected WritableValue<IReminder> item = new WritableValue<>();

	private CDateTime duePicker;

	private BooleanExpandableComposite notifyExpandable;

	private Button popupOnPatient;

	private Button popupOnLogin;

	private Button important;

	public ReminderComposite(Composite parent, int style) {
		super(parent, style);
		CoreUiUtil.injectServices(this);

		setLayout(new GridLayout(3, false));

		Composite leftComposite = new Composite(this, SWT.NONE);
		leftComposite.setLayout(new GridLayout(1, false));
		GridData gd = new GridData(SWT.FILL, SWT.FILL, true, true);
		gd.widthHint = 300;
		leftComposite.setLayoutData(gd);

		Composite middleComposite = new Composite(this, SWT.NONE);
		middleComposite.setLayout(new GridLayout(1, false));
		gd = new GridData(SWT.FILL, SWT.FILL, true, true);
		gd.widthHint = 400;
		middleComposite.setLayoutData(gd);

		Composite rightComposite = new Composite(this, SWT.NONE);
		rightComposite.setLayout(new GridLayout(1, false));
		gd = new GridData(SWT.FILL, SWT.FILL, true, true);
		gd.widthHint = 300;
		rightComposite.setLayoutData(gd);

		typeViewer = new ComboViewer(leftComposite);
		typeViewer.setContentProvider(new ArrayContentProvider());
		typeViewer.setInput(Type.values());
		typeViewer.setLabelProvider(new LabelProvider() {
			@Override
			public String getText(Object element) {
				if (element instanceof Type) {
					return ((Type) element).getLocaleText();
				}
				return super.getText(element);
			}
		});
		typeViewer.getControl().setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));

		Composite patSearchComposite = new Composite(leftComposite, SWT.NONE);
		patSearchComposite.setLayout(new GridLayout(2, false));
		patSearchComposite.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		patSearchText = new Text(patSearchComposite, SWT.SEARCH | SWT.ICON_SEARCH);
		patSearchText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		patSearchText.setMessage("Patient");
		AsyncContentProposalProvider<IPatient> aopp = new AsyncContentProposalProvider<IPatient>("description1", //$NON-NLS-1$
				"description2", "dob", "code") { //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			@Override
			public IQuery<IPatient> createBaseQuery() {
				return CoreModelServiceHolder.get().getQuery(IPatient.class);
			}

			@Override
			protected boolean isPatientQuery() {
				return true;
			}

			@Override
			public Text getWidget() {
				return patSearchText;
			}
		};
		patSearchText.setData(null);
		patSearchText.setTextLimit(80);

		ContentProposalAdapter cppa = new ContentProposalAdapter(patSearchText, new TextContentAdapter(), aopp, null,
				null);
		aopp.configureContentProposalAdapter(cppa);

		cppa.addContentProposalListener(new IContentProposalListener() {

			@SuppressWarnings("unchecked")
			@Override
			public void proposalAccepted(IContentProposal proposal) {
				ch.elexis.core.ui.e4.fieldassist.IdentifiableContentProposal<IPatient> prop = (ch.elexis.core.ui.e4.fieldassist.IdentifiableContentProposal<IPatient>) proposal;
				patSearchText.setText(prop.getLabel());
				patSearchText.setData(prop.getIdentifiable());
				item.getValue().setContact(prop.getIdentifiable());
			}
		});
		ToolBarManager tbManager = new ToolBarManager(SWT.FLAT | SWT.HORIZONTAL | SWT.WRAP);
		tbManager.add(new Action() {
			{
				setImageDescriptor(Images.IMG_DELETE.getImageDescriptor());
			}

			@Override
			public void run() {
				item.getValue().setContact(null);
				updatePatientField();
			}
		});
		tbManager.createControl(patSearchComposite);

		Composite userSearchComposite = new Composite(leftComposite, SWT.NONE);
		userSearchComposite.setLayout(new GridLayout(2, false));
		userSearchComposite.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		userSearchText = new Text(userSearchComposite, SWT.SEARCH | SWT.ICON_SEARCH);
		userSearchText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		userSearchText.setMessage("Zugewiesen User");
		IdentifiableProposalProvider<IUser> userProposalProvider = new IdentifiableProposalProvider<IUser>(
				CoreModelServiceHolder.get().getQuery(IUser.class).and("kontakt", COMPARATOR.NOT_EQUALS, null) //$NON-NLS-1$
						.and("active", COMPARATOR.EQUALS, true)) {

			@SuppressWarnings("unchecked")
			@Override
			public IContentProposal[] getProposals(String contents, int position) {
				List<IContentProposal> list = new ArrayList<>(Arrays.asList(super.getProposals(contents, position)));
				Collections.sort(list, (l, r) -> {
					IdentifiableContentProposal<IUser> lip = (IdentifiableContentProposal<IUser>) l;
					IdentifiableContentProposal<IUser> rip = (IdentifiableContentProposal<IUser>) r;
					if (lip.getIdentifiable().getId().startsWith(contents)
							&& !rip.getIdentifiable().getId().startsWith(contents)) {
						return -1;
					} else if (rip.getIdentifiable().getId().startsWith(contents)
							&& !lip.getIdentifiable().getId().startsWith(contents)) {
						return 1;
					}
					return lip.getIdentifiable().getId().compareTo(rip.getIdentifiable().getId());
				});
				if (Messages.Core_All.toLowerCase().contains(contents.toLowerCase()) || StringUtils.isBlank(contents)) {
					list.add(0, new ContentProposal(Messages.Core_All));
				}
				return list.toArray(new IContentProposal[list.size()]);
			}

			@Override
			public String getLabelForObject(IUser user) {
				return getUserLabel(user);
			}
		};
		userProposalProvider.allowNoContent();
		userProposalProvider.matchContained();
		userSearchText.setData(null);
		userSearchText.setTextLimit(80);

		ContentProposalAdapter cppau = new ContentProposalAdapter(userSearchText, new TextContentAdapter(),
				userProposalProvider, null, null);
		userSearchText.addKeyListener(new KeyAdapter() {
			@Override
			public void keyPressed(KeyEvent e) {
				if (e.keyCode == SWT.ARROW_DOWN) {
					cppau.openProposalPopup();
				}
			}
		});
		cppau.setAutoActivationDelay(250);
		cppau.setProposalAcceptanceStyle(ContentProposalAdapter.PROPOSAL_REPLACE);
		cppau.addContentProposalListener(new IContentProposalListener() {

			@SuppressWarnings("unchecked")
			@Override
			public void proposalAccepted(IContentProposal proposal) {
				if (proposal instanceof IdentifiableContentProposal) {
					IdentifiableContentProposal<IUser> prop = (IdentifiableContentProposal<IUser>) proposal;
					item.getValue().setGroup(null);
					item.getValue().getResponsible().forEach(c -> {
						item.getValue().removeResponsible(c);
					});
					item.getValue().addResponsible(prop.getIdentifiable().getAssignedContact());
					item.getValue().setResponsibleAll(false);
					updateUserResponsibleFields();
				} else {
					item.getValue().setGroup(null);
					item.getValue().setResponsibleAll(true);
					item.getValue().getResponsible().forEach(c -> {
						item.getValue().removeResponsible(c);
					});
					updateUserResponsibleFields();
				}
			}
		});

		Composite groupSearchComposite = new Composite(leftComposite, SWT.NONE);
		groupSearchComposite.setLayout(new GridLayout(2, false));
		groupSearchComposite.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		groupSearchText = new Text(groupSearchComposite, SWT.SEARCH | SWT.ICON_SEARCH);
		groupSearchText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		groupSearchText.setMessage("Zugewiesen Gruppe");
		IdentifiableProposalProvider<IUserGroup> groupProposalProvider = new IdentifiableProposalProvider<IUserGroup>(
				CoreModelServiceHolder.get().getQuery(IUserGroup.class));
		groupProposalProvider.allowNoContent();
		groupSearchText.setData(null);
		groupSearchText.setTextLimit(80);

		ContentProposalAdapter cppag = new ContentProposalAdapter(groupSearchText, new TextContentAdapter(),
				groupProposalProvider, null, null);
		groupSearchText.addKeyListener(new KeyAdapter() {
			@Override
			public void keyPressed(KeyEvent e) {
				if (e.keyCode == SWT.ARROW_DOWN) {
					cppag.openProposalPopup();
				}
			}
		});
		cppag.setAutoActivationDelay(250);
		cppag.setProposalAcceptanceStyle(ContentProposalAdapter.PROPOSAL_REPLACE);
		cppag.addContentProposalListener(new IContentProposalListener() {

			@SuppressWarnings("unchecked")
			@Override
			public void proposalAccepted(IContentProposal proposal) {
				if (proposal instanceof IdentifiableContentProposal) {
					IdentifiableContentProposal<IUserGroup> prop = (IdentifiableContentProposal<IUserGroup>) proposal;
					item.getValue().getResponsible().forEach(c -> {
						item.getValue().removeResponsible(c);
					});
					item.getValue().setResponsibleAll(false);
					item.getValue().setGroup(prop.getIdentifiable());
					updateGroupResponsibleFields();
				}
			}
		});

		subjectTxt = new Text(middleComposite, SWT.BORDER);
		subjectTxt.setMessage("Titel");
		gd = new GridData(SWT.FILL, SWT.TOP, true, false);
		gd.widthHint = 400;
		subjectTxt.setLayoutData(gd);

		msgText = new Text(middleComposite, SWT.BORDER | SWT.MULTI | SWT.WRAP);
		msgText.setMessage("Beschreibung");
		gd = new GridData(SWT.FILL, SWT.FILL, true, true);
		gd.widthHint = 400;
		msgText.setLayoutData(gd);
		
		dueExpandable = new BooleanExpandableComposite(rightComposite, SWT.NONE);
		dueExpandable.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
		dueExpandable.setMessage("Fälligkeit");
		dueExpandable.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				if (dueExpandable.getSelection()) {
					item.getValue().setDue(LocalDate.now());
					duePicker.setSelection(TimeUtil.toDate(LocalDate.now()));
					dueExpandable.setExpanded(true);
				} else {
					item.getValue().setDue(null);
					dueExpandable.setExpanded(false);
				}
			}
		});
		dueExpandable.onExpand(() -> {
			duePicker.setSelection(item.getValue().getDue() != null ? TimeUtil.toDate(item.getValue().getDue()) : null);
		});
		duePicker = new CDateTime(dueExpandable, CDT.BORDER | CDT.SIMPLE);
		duePicker.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 2, 1));
		duePicker.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				item.getValue().setDue(TimeUtil.toLocalDate(duePicker.getSelection()));
			}
		});
		dueExpandable.setExpanded(false);

		notifyExpandable = new BooleanExpandableComposite(rightComposite, SWT.NONE);
		notifyExpandable.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false, 2, 1));
		notifyExpandable.setMessage("Erinnerung");
		notifyExpandable.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				if (notifyExpandable.getSelection()) {
					if (item.getValue().getContact() != null && item.getValue().getContact().isPatient()) {
						item.getValue().setVisibility(Visibility.POPUP_ON_PATIENT_SELECTION);
						popupOnPatient.setSelection(true);
					}
					notifyExpandable.setExpanded(true);
				} else {
					item.getValue().setVisibility(Visibility.ALWAYS);
					notifyExpandable.setExpanded(false);
				}
			}
		});
		notifyExpandable.onExpand(() -> {
			popupOnPatient.setSelection(item.getValue().getVisibility() == Visibility.POPUP_ON_PATIENT_SELECTION);
			popupOnLogin.setSelection(item.getValue().getVisibility() == Visibility.POPUP_ON_LOGIN);
		});
		popupOnPatient = new Button(notifyExpandable, SWT.CHECK);
		popupOnPatient.setText("Bei Patientenauswahl");
		popupOnPatient.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false, 2, 1));
		popupOnPatient.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				item.getValue().setVisibility(Visibility.POPUP_ON_PATIENT_SELECTION);
				popupOnLogin.setSelection(false);
			}
		});

		popupOnLogin = new Button(notifyExpandable, SWT.CHECK);
		popupOnLogin.setText("Bei Login");
		popupOnLogin.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false, 2, 1));
		popupOnLogin.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				item.getValue().setVisibility(Visibility.POPUP_ON_LOGIN);
				popupOnPatient.setSelection(false);
			}
		});

		notifyExpandable.setExpanded(false);

		Label separator = new Label(rightComposite, SWT.SEPARATOR | SWT.HORIZONTAL);
		separator.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));

		important = new Button(rightComposite, SWT.CHECK);
		important.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false, 2, 1));
		important.setText("Wichtig");
		important.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				if (important.getSelection()) {
					item.getValue().setPriority(Priority.HIGH);
				} else {
					item.getValue().setPriority(Priority.MEDIUM);
				}
			}
		});

		initDataBindings();
	}

	public void setReminder(IReminder reminder) {
		item.setValue(reminder);

		updatePatientField();
		updateUserResponsibleFields();
		updateGroupResponsibleFields();

		dueExpandable.setSelection(reminder.getDue() != null);
		dueExpandable.setExpanded(reminder.getDue() != null);
		notifyExpandable.setSelection(reminder.getVisibility() == Visibility.POPUP_ON_PATIENT_SELECTION
				|| reminder.getVisibility() == Visibility.POPUP_ON_LOGIN);
		important.setSelection(reminder.getPriority() == Priority.HIGH);
	}

	private void updatePatientField() {
		IContact patient = reloadAsPatient(item.getValue().getContact());
		if (patient != null) {
			patSearchText.setText(patient.getLabel());
			patSearchText.setData(patient);
		} else {
			patSearchText.setText(StringUtils.EMPTY);
			patSearchText.setData(null);
		}
	}

	private String getUserLabel(IUser user) {
		StringBuilder sb = new StringBuilder(user.getLabel());
		if (user.getAssignedContact() != null) {
			sb.append(" (" + user.getAssignedContact().getDescription1() + StringUtils.SPACE
					+ user.getAssignedContact().getDescription2() + ")");
		}
		return sb.toString();
	}

	private void updateGroupResponsibleFields() {
		IReminder reminder = item.getValue();
		if (reminder.getGroup() != null) {
			groupSearchText.setText(reminder.getGroup().getLabel());
			groupSearchText.setData(reminder.getGroup());
		} else {
			groupSearchText.setText(StringUtils.EMPTY);
			groupSearchText.setData(null);
		}
	}

	private void updateUserResponsibleFields() {
		IReminder reminder = item.getValue();
		if (reminder.getResponsible() != null && !reminder.getResponsible().isEmpty()) {
			List<IUser> user = userService.getUsersByAssociatedContact(reminder.getResponsible().get(0));
			if (!user.isEmpty()) {
				userSearchText.setText(getUserLabel(user.get(0)));
				userSearchText.setData(user.get(0));
			}
		} else if (reminder.isResponsibleAll()) {
			userSearchText.setText(Messages.Core_All);
			userSearchText.setData(Messages.Core_All);
		} else if (reminder.getResponsible() == null || reminder.getResponsible().isEmpty()) {
			userSearchText.setText(StringUtils.EMPTY);
			userSearchText.setData(null);
		}
	}

	private IContact reloadAsPatient(IContact contact) {
		if (contact != null && contact.isPatient()) {
			return CoreModelServiceHolder.get().load(contact.getId(), IPatient.class).orElse(null);
		}
		return null;
	}

	public IReminder getReminder() {
		return item.getValue();
	}

	protected void initDataBindings() {
		DataBindingContext bindingContext = new DataBindingContext();

		IViewerObservableValue<Object> targetObservable = ViewerProperties.singleSelection().observe(typeViewer);
		IObservableValue<Object> modelObservable = PojoProperties.value(IReminder.class, "type")
				.observeDetail(item);
		bindingContext.bindValue(targetObservable, modelObservable);

		ISWTObservableValue<String> target = WidgetProperties.text(SWT.Modify).observeDelayed(500, subjectTxt);
		IObservableValue<Object> model = PojoProperties.value(IReminder.class, "subject")
				.observeDetail(item);
		bindingContext.bindValue(target, model, null, null);

		target = WidgetProperties.text(SWT.Modify).observeDelayed(500, msgText);
		model = PojoProperties.value(IReminder.class, "message").observeDetail(item);
		bindingContext.bindValue(target, model, null, null);
	}

	@Override
	public void dispose() {
		CoreUiUtil.uninjectServices(this);
		super.dispose();
	}
}
