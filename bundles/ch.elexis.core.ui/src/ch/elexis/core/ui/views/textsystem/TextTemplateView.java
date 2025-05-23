package ch.elexis.core.ui.views.textsystem;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExtension;
import org.eclipse.core.runtime.IExtensionPoint;
import org.eclipse.core.runtime.IExtensionRegistry;
import org.eclipse.core.runtime.Platform;
import org.eclipse.e4.core.di.annotations.Optional;
import org.eclipse.e4.ui.di.UIEventTopic;
import org.eclipse.e4.ui.model.application.ui.basic.MPart;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.layout.TableColumnLayout;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.CellEditor;
import org.eclipse.jface.viewers.CheckboxCellEditor;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.ColumnViewerToolTipSupport;
import org.eclipse.jface.viewers.ColumnWeightData;
import org.eclipse.jface.viewers.ComboBoxCellEditor;
import org.eclipse.jface.viewers.DoubleClickEvent;
import org.eclipse.jface.viewers.EditingSupport;
import org.eclipse.jface.viewers.IDoubleClickListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.StructuredViewer;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerComparator;
import org.eclipse.jface.window.ToolTip;
import org.eclipse.swt.SWT;
import org.eclipse.swt.dnd.DND;
import org.eclipse.swt.dnd.DragSourceAdapter;
import org.eclipse.swt.dnd.DragSourceEvent;
import org.eclipse.swt.dnd.FileTransfer;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.events.KeyAdapter;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.forms.widgets.Form;
import org.eclipse.ui.forms.widgets.FormText;
import org.eclipse.ui.handlers.IHandlerService;
import org.eclipse.ui.part.ViewPart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.elexis.core.common.ElexisEventTopics;
import ch.elexis.core.constants.Preferences;
import ch.elexis.core.data.activator.CoreHub;
import ch.elexis.core.data.util.Extensions;
import ch.elexis.core.model.IDocumentLetter;
import ch.elexis.core.ui.UiDesk;
import ch.elexis.core.ui.commands.LoadTemplateCommand;
import ch.elexis.core.ui.constants.ExtensionPointConstantsUi;
import ch.elexis.core.ui.e4.util.CoreUiUtil;
import ch.elexis.core.ui.icons.Images;
import ch.elexis.core.ui.text.ITextPlugin;
import ch.elexis.core.ui.text.ITextTemplateRequirement;
import ch.elexis.core.ui.util.SWTHelper;
import ch.elexis.core.ui.views.textsystem.model.TextTemplate;
import ch.elexis.core.ui.views.textsystem.provider.TextTemplateFilter;
import ch.elexis.core.ui.views.textsystem.provider.TextTemplateViewerComparator;
import ch.elexis.data.Brief;
import ch.elexis.data.Mandant;
import ch.elexis.data.Query;
import ch.rgw.io.FileTool;
import ch.rgw.tools.ExHandler;
import jakarta.inject.Inject;
import jakarta.inject.Named;

public class TextTemplateView extends ViewPart {
	public static final String ID = "ch.elexis.views.textsystem.TextTemplateView"; //$NON-NLS-1$

	private ITextPlugin plugin = null;
	private TableColumnLayout tableLayout;
	private Table table;
	private TableViewer tableViewer;
	private Text txtSearch;
	private TextTemplateFilter searchFilter;
	private TextTemplateViewerComparator comparator;
	private List<TextTemplate> templates;
	private List<TextTemplate> requiredTemplates;

	protected StructuredViewer viewer;

	static Logger log = LoggerFactory.getLogger(TextTemplateView.class);

	@Optional
	@Inject
	void reloadLetter(@UIEventTopic(ElexisEventTopics.EVENT_RELOAD) Class<?> clazz) {
		if (IDocumentLetter.class.isAssignableFrom(clazz)) {
			refresh();
		}
	}

	public TextTemplateView() {
		initActiveTextPlugin();
		loadRequiredAndExistingTemplates();
	}

	private void loadRequiredAndExistingTemplates() {
		requiredTemplates = new ArrayList<>();
		if (plugin == null) {
			return;
		}

		// load required text templates
		List<ITextTemplateRequirement> requirements = Extensions
				.getClasses(ExtensionPointConstantsUi.TEXT_TEMPLATE_REQUIREMENT, "element"); //$NON-NLS-1$
		for (ITextTemplateRequirement txtTemplateReq : requirements) {
			String[] names = txtTemplateReq.getNamesOfRequiredTextTemplate();
			String[] descriptions = txtTemplateReq.getDescriptionsOfRequiredTextTemplate();

			for (int i = 0; i < names.length; i++) {
				TextTemplate tt = new TextTemplate(names[i], descriptions[i], plugin.getMimeType(), true);
				requiredTemplates.add(tt);
			}
		}
		refresh();
	}

	/**
	 * adds a reference to a texttemplate required from the system or creates a form
	 * template entry
	 *
	 * @param txtTemplates required templates
	 * @param template     template from the database to evaluate
	 * @return a {@link TextTemplate} if a formTemplate was added or null if a
	 *         {@link Brief} template was added as reference for a requirement
	 */
	private TextTemplate createTextTemplateReference(Brief template) {
		for (TextTemplate sysTemplate : requiredTemplates) {
			String mandId = template.getAdressat().getId();
			if (sysTemplate.getName().equals(template.getBetreff())
					&& sysTemplate.getMimeType().equals(template.getMimeType())
					&& (mandId == null || mandId.isEmpty())) {
				// matching system template exists - add reference to model
				sysTemplate.addSystemTemplateReference(template);
				return sysTemplate;
			}
		}

		// create TextTemplate model for the form template
		TextTemplate formTemplate = new TextTemplate(template.getBetreff(), StringUtils.EMPTY, template.getMimeType());
		formTemplate.addFormTemplateReference(template);
		return formTemplate;
	}

	@Override
	public void createPartControl(Composite parent) {
		Composite composite = new Composite(parent, SWT.NONE);
		composite.setLayout(new GridLayout(2, false));

		// only display warning if no textplugin is installed
		if (plugin == null) {
			createTextPluginMissingForm(composite);
			return;
		}

		Label lblSearch = new Label(composite, SWT.NONE);
		lblSearch.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
		lblSearch.setText("Suchen: ");
		txtSearch = new Text(composite, SWT.BORDER | SWT.SEARCH);
		txtSearch.setLayoutData(new GridData(GridData.GRAB_HORIZONTAL | GridData.HORIZONTAL_ALIGN_FILL));
		txtSearch.addKeyListener(new KeyAdapter() {
			public void keyReleased(KeyEvent ke) {
				searchFilter.setSearchTerm(txtSearch.getText());
				tableViewer.refresh();
			}
		});

		Composite tableArea = new Composite(composite, SWT.NONE);
		tableArea.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 2, 1));
		tableLayout = new TableColumnLayout();
		tableArea.setLayout(tableLayout);

		tableViewer = new TableViewer(tableArea, SWT.BORDER | SWT.FULL_SELECTION | SWT.V_SCROLL | SWT.MULTI);
		ColumnViewerToolTipSupport.enableFor(tableViewer, ToolTip.NO_RECREATE);
		createColumns(composite);
		table = tableViewer.getTable();
		table.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 2, 1));
		table.setHeaderVisible(true);
		table.setLinesVisible(true);
		tableViewer.setContentProvider(ArrayContentProvider.getInstance());
		tableViewer.setInput(templates);

		// add tableViewer filter
		searchFilter = new TextTemplateFilter();
		tableViewer.addFilter(searchFilter);
		// add tableViewer sort mechanism
		comparator = new TextTemplateViewerComparator();
		tableViewer.setComparator(comparator);
		// add double click listener
		tableViewer.addDoubleClickListener(new IDoubleClickListener() {
			@Override
			public void doubleClick(DoubleClickEvent event) {
				IHandlerService handlerService = (IHandlerService) getSite().getService(IHandlerService.class);
				try {
					handlerService.executeCommand(LoadTemplateCommand.ID, null);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});

		// Create a menu manager, context and register it
		MenuManager menuManager = new MenuManager();
		Menu popupMenu = menuManager.createContextMenu(tableViewer.getTable());
		table.setMenu(popupMenu);
		getSite().registerContextMenu(menuManager, tableViewer);
		getSite().setSelectionProvider(tableViewer);

		final Transfer[] transfer = new Transfer[] { FileTransfer.getInstance(), TextTransfer.getInstance() };
		tableViewer.addDragSupport(DND.DROP_COPY, transfer, new DragSourceAdapter() {
			public void dragStart(DragSourceEvent event) {
				IStructuredSelection selection = (IStructuredSelection) tableViewer.getSelection();
				for (Object object : selection.toList()) {
					TextTemplate textTemplate = (TextTemplate) object;
					if (textTemplate.getTemplate() == null) {
						event.doit = false;
						log.error("Error template doesn't exist"); //$NON-NLS-1$
					}
				}
			}

			public void dragSetData(DragSourceEvent event) {
				IStructuredSelection selection = (IStructuredSelection) tableViewer.getSelection();
				if (FileTransfer.getInstance().isSupportedType(event.dataType)) {
					String[] files = new String[selection.size()];
					for (int i = 0; i < selection.size(); i++) {
						TextTemplate textTemplate = (TextTemplate) selection.toList().get(i);
						File file = new File(textTemplate.getName() + "." + textTemplate.getMimeType()); //$NON-NLS-1$
						byte[] contents = textTemplate.getTemplate().loadBinary();

						try (ByteArrayInputStream bais = new ByteArrayInputStream(contents);
								FileOutputStream fos = new FileOutputStream(file);) {
							FileTool.copyStreams(bais, fos);
						} catch (IOException e) {
							log.error("Error creating template", e); //$NON-NLS-1$
						}
						files[i] = file.getAbsolutePath();
						log.debug("dragSetData; isSupportedType {} data {}", file.getAbsolutePath(), //$NON-NLS-1$
								event.data);
					}
					event.data = files;
				}
			}
		});
	}

	private void createTextPluginMissingForm(Composite parent) {
		String expl = Messages.TextTemplateVeiw_NoTxtPluginDescription + Messages.Text_No_Plugin_loaded
				+ Messages.Text_Plugin_Not_Configured + Messages.Text_External_Cmd_deleted;

		Form form = UiDesk.getToolkit().createForm(parent);
		form.setText(Messages.Core_Unable_to_create_text);
		form.setLayoutData(SWTHelper.fillGrid(parent, 1));
		form.getBody().setLayout(new GridLayout(1, false));
		FormText ft = UiDesk.getToolkit().createFormText(form.getBody(), false);
		ft.setText(expl, true, false);
	}

	/**
	 * create table columns
	 *
	 * @param parent
	 * @param viewer
	 */
	private void createColumns(final Composite parent) {
		String[] titles = { StringUtils.EMPTY, "Name der Vorlage", "Typ", "Mandant", "Adressabfrage", "Drucker/Schacht",
				"Beschreibung" };
		int[] bounds = { 30, 200, 170, 80, 90, 300, 600 };
		
		// template exists or missing
		TableViewerColumn col = createTableViewerColumn(titles[0], bounds[0], 0);
		col.setLabelProvider(new ColumnLabelProvider() {
			@Override
			public Image getImage(Object element) {
				if (element instanceof TextTemplate) {
					TextTemplate template = (TextTemplate) element;
					if (!template.exists()) {
						return Images.IMG_AUSRUFEZ.getImage();
					} else if (template.isSystemTemplate()) {
						return Images.IMG_DOC_SYS.getImage();
					}
				}
				return null;
			}

			@Override
			public String getText(Object element) {
				return null;
			}

			@Override
			public String getToolTipText(Object element) {
				if (element instanceof TextTemplate) {
					TextTemplate template = (TextTemplate) element;
					if (!template.exists()) {
						return "Vorlage nicht vorhanden";
					} else if (template.isSystemTemplate()) {
						return "Systemvorlage";
					} else if (template.getMandantLabel().equals(TextTemplate.DEFAULT_MANDANT)) {
						return "Formatvorlage";
					} else {
						return "Benutzerspezifische Vorlage";
					}
				}
				return null;
			}
		});

		ViewerComparator systemTemplateComparator = new ViewerComparator() {
			@Override
			public int compare(Viewer viewer, Object e1, Object e2) {
				if (e1 instanceof TextTemplate && e2 instanceof TextTemplate) {
					TextTemplate t1 = (TextTemplate) e1;
					TextTemplate t2 = (TextTemplate) e2;
					int result = Boolean.compare(t1.isSystemTemplate(), t2.isSystemTemplate());
					return getSortedAscending(viewer) ? result : -result;
				}
				return 0;
			}

			private boolean getSortedAscending(Viewer viewer) {
				if (viewer.getData("systemTemplateSortAscending") != null) {
					
					return (Boolean) viewer.getData("systemTemplateSortAscending");
				}
				return true;
			}
		};

		col.getColumn().addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				tableViewer.setComparator(systemTemplateComparator);
				if (tableViewer.getComparator() == systemTemplateComparator) {
					if (tableViewer.getData("systemTemplateSortAscending") != null) {
						tableViewer.setData("systemTemplateSortAscending",
								!(Boolean) tableViewer.getData("systemTemplateSortAscending"));
					} else {
						tableViewer.setData("systemTemplateSortAscending", Boolean.FALSE);
					}
					tableViewer.refresh();
				}
			}
		});

		// template name
		col = createTableViewerColumn(titles[1], bounds[1], 1);
		col.setLabelProvider(new ColumnLabelProvider() {
			@Override
			public String getText(Object element) {
				if (element instanceof TextTemplate) {
					TextTemplate template = (TextTemplate) element;
					return template.getName();
				}
				return super.getText(element);
			}

			@Override
			public Color getForeground(Object element) {
				return getForegroundColor(element);
			}
		});

		// mime type
		col = createTableViewerColumn(titles[2], bounds[2], 2);
		col.setLabelProvider(new ColumnLabelProvider() {
			@Override
			public String getText(Object element) {
				if (element instanceof TextTemplate) {
					TextTemplate template = (TextTemplate) element;
					return template.getMimeTypePrintname();
				}
				return super.getText(element);
			}

			@Override
			public Color getForeground(Object element) {
				return getForegroundColor(element);
			}
		});

		// mandant
		col = createTableViewerColumn(titles[3], bounds[3], 3);
		col.setLabelProvider(new ColumnLabelProvider() {
			@Override
			public String getText(Object element) {
				if (element instanceof TextTemplate) {
					TextTemplate template = (TextTemplate) element;
					return template.getMandantLabel();
				}
				return super.getText(element);
			}

			@Override
			public Color getForeground(Object element) {
				return getForegroundColor(element);
			}
		});
		col.setEditingSupport(new MandantEditingSupport(tableViewer));

		// address required
		col = createTableViewerColumn(titles[4], bounds[4], 4);
		col.setLabelProvider(new ColumnLabelProvider() {
			@Override
			public Image getImage(Object element) {
				if (element instanceof TextTemplate) {
					TextTemplate template = (TextTemplate) element;
					if (template.askForAddress()) {
						return Images.IMG_CHECKBOX.getImage();
					}
				}
				return Images.IMG_CHECKBOX_UNCHECKED.getImage();
			}

			@Override
			public String getText(Object element) {
				return null;
			}
		});
		col.setEditingSupport(new AddressRequiredEditingSupport(tableViewer));

		// printer
		col = createTableViewerColumn(titles[5], bounds[5], 5);
		col.setLabelProvider(new ColumnLabelProvider() {
			@Override
			public String getText(Object element) {
				if (element instanceof TextTemplate) {
					TextTemplate template = (TextTemplate) element;
					String label = StringUtils.EMPTY;
					String printer = template.getPrinter();
					String tray = template.getTray();
					if (printer != null) {
						label += printer;
					}
					if (tray != null) {
						label += "/ " + tray; //$NON-NLS-1$
					}
					return label;
				}
				return super.getText(element);
			}

			@Override
			public Color getForeground(Object element) {
				return getForegroundColor(element);
			}
		});

		// description
		col = createTableViewerColumn(titles[6], bounds[6], 6);
		col.setLabelProvider(new ColumnLabelProvider() {
			@Override
			public String getText(Object element) {
				if (element instanceof TextTemplate) {
					TextTemplate template = (TextTemplate) element;
					return template.getDescription();
				}
				return super.getText(element);
			}

			@Override
			public Color getForeground(Object element) {
				return getForegroundColor(element);
			}
		});
	}

	private Color getForegroundColor(Object element) {
		if (element instanceof TextTemplate) {
			TextTemplate template = (TextTemplate) element;
			if (!template.exists()) {
				return UiDesk.getColor(UiDesk.COL_GREY);
			}
		}
		return null;
	}

	private TableViewerColumn createTableViewerColumn(String title, int bound, final int colNumber) {
		final TableViewerColumn viewerColumn = new TableViewerColumn(tableViewer, SWT.NONE);
		final TableColumn column = viewerColumn.getColumn();
		tableLayout.setColumnData(column, new ColumnWeightData(bound, ColumnWeightData.MINIMUM_WIDTH, true));
		column.setText(title);
		column.setResizable(true);
		column.setMoveable(false);
		column.addSelectionListener(getSelectionAdapter(column, colNumber));
		return viewerColumn;
	}

	private SelectionAdapter getSelectionAdapter(final TableColumn column, final int index) {
		SelectionAdapter selectionAdapter = new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				comparator.setColumn(index);
				int dir = comparator.getDirection();
				tableViewer.getTable().setSortDirection(dir);
				tableViewer.getTable().setSortColumn(column);
				tableViewer.refresh();
			}
		};
		return selectionAdapter;
	}

	class MandantEditingSupport extends EditingSupport {
		private TableViewer tableViewer;
		private List<Mandant> mandants;

		public MandantEditingSupport(TableViewer tableViewer) {
			super(tableViewer);
			this.tableViewer = tableViewer;
			this.mandants = new ArrayList<>();
		}

		@Override
		protected CellEditor getCellEditor(Object element) {
			Query<Mandant> qbe = new Query<>(Mandant.class);
			mandants = qbe.execute();

			String[] mandantArray = new String[mandants.size() + 1];
			mandantArray[0] = TextTemplate.DEFAULT_MANDANT;
			for (int i = 0; i < mandants.size(); i++) {
				mandantArray[i + 1] = mandants.get(i).getLabel();
			}

			return new ComboBoxCellEditor(tableViewer.getTable(), mandantArray, SWT.READ_ONLY);
		}

		@Override
		protected boolean canEdit(Object element) {
			TextTemplate template = (TextTemplate) element;
			return !template.isSystemTemplate();
		}

		@Override
		protected Object getValue(Object element) {
			TextTemplate template = (TextTemplate) element;
			Mandant mandant = template.getMandant();
			int index = mandants.indexOf(mandant);
			return index + 1;
		}

		@Override
		protected void setValue(Object element, Object value) {
			TextTemplate template = (TextTemplate) element;
			int index = ((Integer) value) - 1;

			// all
			if (index == -1) {
				template.prepareRewrite();
				template.setMandant(StringUtils.EMPTY);
				template.rewrite();
			} else {
				// specific mandant only
				template.prepareRewrite();
				Mandant mandant = mandants.get(index);
				template.setMandant(mandant.getId());
				template.setSystemTemplate(false);
				template.rewrite();
			}
			tableViewer.update(element, null);
		}
	}

	class AddressRequiredEditingSupport extends EditingSupport {
		private TableViewer tableViewer;

		public AddressRequiredEditingSupport(TableViewer tableViewer) {
			super(tableViewer);
			this.tableViewer = tableViewer;
		}

		@Override
		protected CellEditor getCellEditor(Object element) {
			// Native Checkboxes not available:
			// https://bugs.eclipse.org/bugs/show_bug.cgi?id=260061
			return new CheckboxCellEditor(tableViewer.getTable(), SWT.CHECK | SWT.READ_ONLY);
		}

		@Override
		protected boolean canEdit(Object element) {
			return true;
		}

		@Override
		protected Object getValue(Object element) {
			TextTemplate template = (TextTemplate) element;
			return template.askForAddress();
		}

		@Override
		protected void setValue(Object element, Object value) {
			TextTemplate template = (TextTemplate) element;
			template.setAskForAddress((Boolean) value);
			tableViewer.update(element, null);
		}
	}

	@Override
	public void setFocus() {
		// TODO Auto-generated method stub
	}

	private void initActiveTextPlugin() {
		if (plugin == null) {
			String ExtensionToUse = CoreHub.localCfg.get(Preferences.P_TEXTMODUL, null);
			IExtensionRegistry exr = Platform.getExtensionRegistry();
			IExtensionPoint exp = exr.getExtensionPoint(ExtensionPointConstantsUi.TEXTPROCESSINGPLUGIN);
			if (exp != null) {
				IExtension[] extensions = exp.getExtensions();
				for (IExtension ex : extensions) {
					IConfigurationElement[] elems = ex.getConfigurationElements();
					for (IConfigurationElement el : elems) {
						if ((ExtensionToUse == null) || el.getAttribute("name").equals( //$NON-NLS-1$
								ExtensionToUse)) {
							try {
								plugin = (ITextPlugin) el.createExecutableExtension("Klasse"); //$NON-NLS-1$
							} catch (Exception e) {
								ExHandler.handle(e);
							}
						}

					}
				}
			}
		}
	}

	public ITextPlugin getActiveTextPlugin() {
		return plugin;
	}

	public List<TextTemplate> getRequiredTextTemplates() {
		return requiredTemplates;
	}

	public void update(TextTemplate textTemplate) {
		int index = templates.indexOf(textTemplate);
		if (index == -1) {
			templates.add(textTemplate);
		} else {
			templates.set(index, textTemplate);
		}
		tableViewer.refresh();
	}

	private void refresh() {
		// load existing templates from database
		Query<Brief> qbe = new Query<>(Brief.class);
		qbe.add(Brief.FLD_TYPE, Query.EQUALS, Brief.TEMPLATE);
		List<Brief> list = qbe.execute();

		List<TextTemplate> txtTemplates = new ArrayList<>();
		for (Brief template : list) {
			txtTemplates.add(createTextTemplateReference(template));
		}

		for (TextTemplate reqTemplate : requiredTemplates) {
			if (!txtTemplates.contains(reqTemplate)) {
				txtTemplates.add(reqTemplate);
			}
		}
		templates = txtTemplates;
		if (tableViewer != null && !tableViewer.getControl().isDisposed()) {
			tableViewer.setInput(templates);
			tableViewer.refresh(true);
		}
	}

	@Optional
	@Inject
	public void setFixLayout(MPart part, @Named(Preferences.USR_FIX_LAYOUT) boolean currentState) {
		CoreUiUtil.updateFixLayout(part, currentState);
	}
}
