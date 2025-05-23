/*******************************************************************************
 * Copyright (c) 2017 MEDEVIT <office@medevit.at>.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     MEDEVIT <office@medevit.at> - initial API and implementation
 ******************************************************************************/
package ch.elexis.core.ui.views.rechnung;

import static ch.elexis.core.ui.constants.ExtensionPointConstantsUi.VIEWCONTRIBUTION;
import static ch.elexis.core.ui.constants.ExtensionPointConstantsUi.VIEWCONTRIBUTION_CLASS;
import static ch.elexis.core.ui.constants.ExtensionPointConstantsUi.VIEWCONTRIBUTION_VIEWID;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.e4.core.di.annotations.Optional;
import org.eclipse.e4.ui.di.UIEventTopic;
import org.eclipse.e4.ui.model.application.ui.basic.MPart;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.action.ToolBarManager;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IPageChangedListener;
import org.eclipse.jface.dialogs.InputDialog;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.dialogs.PageChangedEvent;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.layout.TableColumnLayout;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.ColumnViewerToolTipSupport;
import org.eclipse.jface.viewers.ColumnWeightData;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerComparator;
import org.eclipse.jface.window.ToolTip;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.dnd.DropTargetEvent;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Link;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.ToolBar;
import org.eclipse.ui.IWorkbenchActionConstants;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.commands.ICommandService;
import org.eclipse.ui.forms.events.ExpansionAdapter;
import org.eclipse.ui.forms.events.ExpansionEvent;
import org.eclipse.ui.forms.widgets.ExpandableComposite;
import org.eclipse.ui.forms.widgets.FormToolkit;
import org.eclipse.ui.forms.widgets.ScrolledForm;
import org.eclipse.ui.part.ViewPart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.elexis.core.common.ElexisEventTopics;
import ch.elexis.core.constants.Preferences;
import ch.elexis.core.constants.StringConstants;
import ch.elexis.core.data.interfaces.IFall;
import ch.elexis.core.data.interfaces.IPersistentObject;
import ch.elexis.core.data.service.LocalLockServiceHolder;
import ch.elexis.core.data.util.BillingUtil;
import ch.elexis.core.data.util.BillingUtil.BillCallback;
import ch.elexis.core.data.util.Extensions;
import ch.elexis.core.data.util.NoPoUtil;
import ch.elexis.core.exceptions.ElexisException;
import ch.elexis.core.l10n.Messages;
import ch.elexis.core.model.IBillable;
import ch.elexis.core.model.IBilled;
import ch.elexis.core.model.IDiagnosis;
import ch.elexis.core.model.IEncounter;
import ch.elexis.core.model.IInvoice;
import ch.elexis.core.model.IUser;
import ch.elexis.core.services.IContextService;
import ch.elexis.core.ui.UiDesk;
import ch.elexis.core.ui.actions.CodeSelectorHandler;
import ch.elexis.core.ui.dialogs.DateSelectorDialog;
import ch.elexis.core.ui.dialogs.FallSelectionDialog;
import ch.elexis.core.ui.dialogs.KontaktSelektor;
import ch.elexis.core.ui.dialogs.ResultDialog;
import ch.elexis.core.ui.e4.util.CoreUiUtil;
import ch.elexis.core.ui.icons.Images;
import ch.elexis.core.ui.locks.IUnlockable;
import ch.elexis.core.ui.locks.ToggleCurrentInvoiceLockHandler;
import ch.elexis.core.ui.util.GenericObjectDropTarget;
import ch.elexis.core.ui.util.SWTHelper;
import ch.elexis.core.ui.util.WidgetFactory;
import ch.elexis.core.ui.views.FallDetailBlatt2;
import ch.elexis.core.ui.views.codesystems.DiagnosenView;
import ch.elexis.core.ui.views.codesystems.LeistungenView;
import ch.elexis.core.ui.views.contribution.IViewContribution;
import ch.elexis.core.ui.views.contribution.ViewContributionHelper;
import ch.elexis.core.ui.views.rechnung.InvoiceCorrectionWizard.Page2;
import ch.elexis.data.Konsultation;
import ch.elexis.data.Mandant;
import ch.elexis.data.Rechnung;
import ch.elexis.data.Rechnungssteller;
import ch.elexis.data.dto.DiagnosesDTO;
import ch.elexis.data.dto.FallDTO;
import ch.elexis.data.dto.FallDTO.IFallChanged;
import ch.elexis.data.dto.InvoiceCorrectionDTO;
import ch.elexis.data.dto.InvoiceCorrectionDTO.IInvoiceCorrectionChanged;
import ch.elexis.data.dto.InvoiceHistoryEntryDTO;
import ch.elexis.data.dto.InvoiceHistoryEntryDTO.OperationType;
import ch.elexis.data.dto.KonsultationDTO;
import ch.elexis.data.dto.LeistungDTO;
import ch.rgw.tools.Money;
import ch.rgw.tools.Result;
import ch.rgw.tools.Result.SEVERITY;
import ch.rgw.tools.StringTool;
import ch.rgw.tools.TimeTool;
import jakarta.inject.Inject;
import jakarta.inject.Named;

public class InvoiceCorrectionView extends ViewPart implements IUnlockable {

	public static final String ID = "ch.elexis.core.ui.views.rechnung.InvoiceCorrectionView"; //$NON-NLS-1$
	private InvoiceComposite invoiceComposite;

	private Rechnung actualInvoice;
	private InvoiceCorrectionDTO invoiceCorrectionDTO = new InvoiceCorrectionDTO();

	private InvoiceBottomComposite invoiceBottomComposite;

	private static final Logger log = LoggerFactory.getLogger(InvoiceCorrectionView.class);

	private boolean unlocked;

	@Inject
	private IContextService contextService;

	@SuppressWarnings("unchecked")
	private final List<IViewContribution> detailComposites = Extensions.getClasses(VIEWCONTRIBUTION,
			VIEWCONTRIBUTION_CLASS, VIEWCONTRIBUTION_VIEWID, RnDetailView.ID);

	@Inject
	@Optional
	public void lockReleased(@UIEventTopic(ElexisEventTopics.EVENT_LOCK_RELEASED) IInvoice invoice) {
		if (actualInvoice != null && actualInvoice.getId().equals(invoice.getId())) {
			setUnlocked(false);
		}
	}

	@Inject
	@Optional
	public void lockAquired(@UIEventTopic(ElexisEventTopics.EVENT_LOCK_AQUIRED) IInvoice invoice) {
		if (actualInvoice != null && actualInvoice.getId().equals(invoice.getId())) {
			if (LocalLockServiceHolder.get().acquireLock(actualInvoice.getFall()).isOk()) {
				setUnlocked(true);
			} else {
				MessageDialog.openWarning(UiDesk.getDisplay().getActiveShell(), "Lock nicht erhalten",
						"Lock nicht erhalten. Diese Operation ist derzeit nicht möglich.");
			}
		}
	}

	@Inject
	@Optional
	public void updateInvoice(@UIEventTopic(ElexisEventTopics.EVENT_UPDATE) IInvoice invoice) {
		reloadSameInvoice((Rechnung) NoPoUtil.loadAsPersistentObject(invoice));
	}

	@Inject
	@Optional
	public void deletedInvoice(@UIEventTopic(ElexisEventTopics.EVENT_DELETE) IInvoice invoice) {
		reload(null);
	}

	@Inject
	@Optional
	public void reloadInvoice(@UIEventTopic(ElexisEventTopics.EVENT_RELOAD) Class<?> clazz) {
		if (IInvoice.class.equals(clazz)) {
			reload(actualInvoice);
		}
	}

	@Optional
	@Inject
	void activeInvoice(IInvoice invoice) {
		Display.getDefault().asyncExec(() -> {
			if (actualInvoice != null) {
				releaseAndRefreshLock(actualInvoice, ToggleCurrentInvoiceLockHandler.COMMAND_ID);
				if (LocalLockServiceHolder.get().isLocked(actualInvoice.getFall())) {
					LocalLockServiceHolder.get().releaseLock(actualInvoice.getFall());
				}
			}
			reload((Rechnung) NoPoUtil.loadAsPersistentObject(invoice));
		});
	}

	@Inject
	void activeUser(@Optional IUser user) {
		Display.getDefault().asyncExec(() -> {
			adaptForUser(user);
		});
	}

	private void adaptForUser(IUser user) {
		if (actualInvoice != null) {
			contextService.postEvent(ElexisEventTopics.EVENT_UPDATE,
					NoPoUtil.loadAsIdentifiable(actualInvoice, IInvoice.class));
		} else {
			reload(null);
		}
	}

	private void reloadSameInvoice(Rechnung invoiceToReload) {
		if (actualInvoice != null && invoiceToReload != null
				&& StringUtils.equals(actualInvoice.getId(), invoiceToReload.getId())) {
			reload(invoiceToReload);
		}
	}

	private void reload(Rechnung rechnung) {
		if (invoiceComposite != null) {
			if (rechnung != null && rechnung.exists()) {
				actualInvoice = Rechnung.load(rechnung.getId());
				invoiceCorrectionDTO = new InvoiceCorrectionDTO(actualInvoice);
			} else if (actualInvoice != null && actualInvoice.exists()) {
				actualInvoice = Rechnung.load(actualInvoice.getId());
				invoiceCorrectionDTO = new InvoiceCorrectionDTO(actualInvoice);
			} else {
				actualInvoice = null;
				invoiceCorrectionDTO = new InvoiceCorrectionDTO();
			}
			Composite parent = invoiceComposite.getParent();
			invoiceComposite.dispose();
			invoiceComposite = new InvoiceComposite(parent);
			invoiceComposite.createComponents(invoiceCorrectionDTO);
			parent.layout(true, true);

			if (invoiceCorrectionDTO.getInvoiceNumber() != null && !invoiceCorrectionDTO.getErrors().isEmpty()) {
				StringBuilder builder = new StringBuilder();
				for (ElexisException e : invoiceCorrectionDTO.getErrors()) {
					builder.append(StringUtils.LF + e.getMessage());
				}
				MessageDialog.openWarning(getSite().getShell(), "Rechnungskorrektur",
						"Die Rechnung " + invoiceCorrectionDTO.getInvoiceNumber()
								+ " konnte nicht vollständig geladen werden.\n\nDetails: " + builder.toString());
			}
			setUnlocked(LocalLockServiceHolder.get().isLocked(actualInvoice));
		}
	}

	@Override
	public void createPartControl(Composite parent) {
		parent.setLayout(new GridLayout(1, false));
		invoiceComposite = new InvoiceComposite(parent);
		invoiceComposite.createComponents(invoiceCorrectionDTO);
		java.util.Optional<IInvoice> selected = contextService.getTyped(IInvoice.class);
		if (selected.isPresent()) {
			reload((Rechnung) NoPoUtil.loadAsPersistentObject(selected.get()));
		}
	}

	@Override
	public void dispose() {
		if (actualInvoice != null && isUnlocked()) {
			releaseAndRefreshLock(actualInvoice, ToggleCurrentInvoiceLockHandler.COMMAND_ID);
			LocalLockServiceHolder.get().releaseLock(actualInvoice.getFall());
		}
		super.dispose();
	}

	@Override
	public void setFocus() {
		if (invoiceComposite != null) {
			invoiceComposite.updateScrollBars();
		}
	}

	class InvoiceComposite extends ScrolledComposite implements IUnlockable {
		Composite wrapper;
		InvoiceHeaderComposite invoiceHeaderComposite;
		InvoiceContentComposite invoiceContentComposite;

		public InvoiceComposite(Composite parent) {
			super(parent, SWT.H_SCROLL | SWT.V_SCROLL);
			setLayout(new GridLayout(1, false));
			setLayoutData(SWTHelper.getFillGridData(1, true, 1, true));
		}

		public void createComponents(InvoiceCorrectionDTO invoiceCorrectionDTO) {
			wrapper = new Composite(this, SWT.NONE);
			wrapper.setLayout(new GridLayout(1, false));
			wrapper.setLayoutData(SWTHelper.getFillGridData(1, true, 1, true));

			invoiceHeaderComposite = new InvoiceHeaderComposite(wrapper);
			invoiceContentComposite = new InvoiceContentComposite(wrapper);
			invoiceBottomComposite = new InvoiceBottomComposite(wrapper);

			invoiceHeaderComposite.createComponents(invoiceCorrectionDTO);
			if (invoiceCorrectionDTO != null) {
				if (invoiceCorrectionDTO.getId() != null) {
					invoiceContentComposite.createComponents(invoiceCorrectionDTO);
					invoiceBottomComposite.createComponents();
				}
				invoiceCorrectionDTO.register(new IInvoiceCorrectionChanged() {
					@Override
					public void changed(InvoiceCorrectionDTO invoiceCorrectionDTO) {
						if (invoiceBottomComposite != null) {
							invoiceBottomComposite.refresh(true);
						}
					}
				});
			}
			this.setContent(wrapper);
			this.setExpandHorizontal(true);
			this.setExpandVertical(true);
			updateScrollBars();
		}

		public void updateScrollBars() {
			if (wrapper != null) {
				this.setMinSize(wrapper.computeSize(SWT.DEFAULT, SWT.DEFAULT));
				this.layout(true, true);
			}

		}

		@Override
		public void setUnlocked(boolean unlocked) {
			if (invoiceHeaderComposite != null) {
				invoiceHeaderComposite.setUnlocked(unlocked);
			}
			if (invoiceContentComposite != null) {
				invoiceContentComposite.setUnlocked(unlocked);
			}
			if (invoiceBottomComposite != null) {
				invoiceBottomComposite.setUnlocked(unlocked);
			}
		}

	}

	class InvoiceHeaderComposite extends Composite implements IUnlockable {

		String[] lbls = new String[] { "Rechnung", "Status", "Patient", "Rechnungsbetrag" };

		public InvoiceHeaderComposite(Composite parent) {
			super(parent, SWT.BORDER);

			GridLayout gd = new GridLayout(1, false);
			gd.marginWidth = 0;
			gd.marginHeight = 0;
			setLayout(gd);

			setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false, 1, 1));
		}

		public void createComponents(InvoiceCorrectionDTO invoiceCorrectionDTO) {

			FormToolkit tk = UiDesk.getToolkit();
			ScrolledForm form = tk.createScrolledForm(this);
			form.setBackground(UiDesk.getColor(UiDesk.COL_WHITE));
			form.setLayoutData(SWTHelper.getFillGridData(1, true, 1, false));
			Composite body = form.getBody();
			GridLayout gd1 = new GridLayout();
			gd1.marginWidth = 0;
			gd1.marginHeight = 0;
			body.setLayout(gd1);

			ExpandableComposite expandable = WidgetFactory.createExpandableComposite(tk, form, StringUtils.EMPTY);
			expandable.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));
			expandable.setExpanded(false);
			expandable.setText("Rechnungsangaben");
			expandable.addExpansionListener(new ExpansionAdapter() {

				@Override
				public void expansionStateChanged(ExpansionEvent e) {
					invoiceComposite.updateScrollBars();
				}
			});
			expandable.setExpanded(true);
			Composite group = tk.createComposite(expandable, SWT.NONE);
			GridLayout gd3 = new GridLayout(2, false);
			gd3.marginWidth = 5;
			gd3.marginHeight = 5;
			group.setLayout(gd3);
			group.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));
			expandable.setClient(group);

			Color colWhite = UiDesk.getColor(UiDesk.COL_WHITE);
			this.setBackground(colWhite);

			GridData gd = new GridData(SWT.FILL, SWT.FILL, true, false, 1, 1);
			String[] invoiceDetails = invoiceCorrectionDTO.getInvoiceDetails();
			if (invoiceDetails.length == lbls.length) {
				int i = 0;
				for (String lbl : lbls) {
					String detailText = invoiceDetails[i++];
					new Label(group, SWT.NONE).setText(lbl);
					Text text = new Text(group, SWT.BORDER | SWT.READ_ONLY);
					text.setBackground(colWhite);
					text.setLayoutData(gd);
					text.setText(detailText != null ? detailText : StringUtils.EMPTY);
				}
			}
			new Label(group, SWT.NONE).setText("Bemerkung");
			Text txtMulti = new Text(group, SWT.MULTI | SWT.BORDER | SWT.WRAP | SWT.V_SCROLL | SWT.READ_ONLY);
			GridData gd2 = new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1);
			gd2.heightHint = 50;
			txtMulti.setBackground(UiDesk.getColor(UiDesk.COL_WHITE));
			txtMulti.setLayoutData(gd2);
			txtMulti.setText(invoiceCorrectionDTO.getBemerkung() != null ? invoiceCorrectionDTO.getBemerkung()
					: StringUtils.EMPTY);

			if (invoiceCorrectionDTO.getNewInvoiceNumber() != null) {
				if (!invoiceCorrectionDTO.getNewInvoiceNumber().isEmpty()) {
					new Label(group, SWT.NONE).setText("Korrigierte Rechnung");
					Link btnNewInvoice = new Link(group, SWT.NONE);
					btnNewInvoice.setBackground(UiDesk.getColor(UiDesk.COL_WHITE));
					btnNewInvoice.setText("<A>Rechnung " + invoiceCorrectionDTO.getNewInvoiceNumber() + " öffnen</A>");
					btnNewInvoice.addSelectionListener(new SelectionAdapter() {
						@Override
						public void widgetSelected(SelectionEvent e) {
							openInvoiceNr(invoiceCorrectionDTO.getNewInvoiceNumber());
						}
					});
				}
			}

			if (actualInvoice != null && !detailComposites.isEmpty()) {
				Label separator = new Label(group, SWT.HORIZONTAL | SWT.SEPARATOR);
				separator.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false, 10, 1));
				List<IViewContribution> filtered = ViewContributionHelper
						.getFilteredAndPositionSortedContributions(detailComposites, 0);
				for (IViewContribution ivc : filtered) {
					new Label(group, SWT.NONE).setText(ivc.getLocalizedTitle());

					Composite mainComposite = new Composite(group, SWT.NONE);
					mainComposite.setBackground(UiDesk.getColor(UiDesk.COL_WHITE));
					mainComposite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 2, 1));
					mainComposite.setLayout(new GridLayout(1, false));
					ivc.initComposite(mainComposite);
				}

				detailComposites.forEach(dc -> dc.setDetailObject(actualInvoice, null));
			}
		}

		@Override
		public void setUnlocked(boolean unlocked) {
			// nothing todo all fields are readonly
		}

	}

	private void openInvoiceNr(String invoiceNr) {
		Rechnung r = Rechnung.getFromNr(invoiceNr);
		if (r != null) {
			contextService.setTyped(NoPoUtil.loadAsIdentifiable(r, IInvoice.class));
		} else {
			MessageDialog.openError(UiDesk.getDisplay().getActiveShell(), "Fehler", "Die Rechnung mit der Nummer: "
					+ invoiceNr + " konnte nicht geöffnet werden.\nBitte versuchen Sie diesn manuell zu öffnen.");
		}
	}

	class InvoiceContentComposite extends Composite implements IUnlockable {

		InvoiceContentHeaderComposite invoiceContentHeaderComposite;
		InvoiceContentMiddleComposite invoiceContentMiddleComposite;

		public InvoiceContentComposite(Composite parent) {
			super(parent, SWT.NONE);
			GridLayout gd = new GridLayout(1, false);
			gd.marginWidth = 0;
			gd.marginHeight = 5;
			setLayout(gd);
			setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false, 1, 1));
		}

		public void createComponents(InvoiceCorrectionDTO invoiceCorrectionDTO) {
			if (invoiceCorrectionDTO.getFallDTO() != null) {
				invoiceContentHeaderComposite = new InvoiceContentHeaderComposite(this);
				invoiceContentHeaderComposite.createComponents(invoiceCorrectionDTO.getFallDTO());
			}

			invoiceContentMiddleComposite = new InvoiceContentMiddleComposite(this);
			invoiceContentMiddleComposite.createComponents(invoiceCorrectionDTO);
		}

		@Override
		public void setUnlocked(boolean unlocked) {
			if (invoiceContentHeaderComposite != null) {
				invoiceContentHeaderComposite.setUnlocked(unlocked);
			}
			if (invoiceContentMiddleComposite != null) {
				invoiceContentMiddleComposite.setUnlocked(unlocked);
			}

		}
	}

	class InvoiceContentHeaderComposite extends Composite implements IUnlockable {

		FallDetailBlatt2 fallDetailBlatt2;

		public InvoiceContentHeaderComposite(Composite parent) {
			super(parent, SWT.BORDER);
			GridLayout gd = new GridLayout(1, false);
			gd.marginWidth = 0;
			gd.marginHeight = 0;
			setLayout(gd);
			setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false, 1, 1));
		}

		public void createComponents(FallDTO fallDTO) {
			this.setBackground(UiDesk.getColor(UiDesk.COL_WHITE));
			FormToolkit tk = UiDesk.getToolkit();
			ScrolledForm form = tk.createScrolledForm(this);
			form.setBackground(UiDesk.getColor(UiDesk.COL_WHITE));
			form.setLayoutData(SWTHelper.getFillGridData(1, true, 1, false));
			Composite body = form.getBody();
			GridLayout gd1 = new GridLayout();
			gd1.marginWidth = 0;
			gd1.marginHeight = 0;
			body.setLayout(gd1);
			ExpandableComposite expandable = WidgetFactory.createExpandableComposite(tk, form, StringUtils.EMPTY);
			expandable.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));
			expandable.setExpanded(false);
			expandable.setText("Fallangaben");
			expandable.addExpansionListener(new ExpansionAdapter() {

				@Override
				public void expansionStateChanged(ExpansionEvent e) {
					invoiceComposite.updateScrollBars();
				}
			});
			Composite group = tk.createComposite(expandable, SWT.NONE);
			GridLayout gd = new GridLayout(2, false);
			gd.marginWidth = 0;
			gd.marginHeight = 0;
			group.setLayout(gd);
			group.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));
			expandable.setClient(group);

			fallDetailBlatt2 = new FallDetailBlatt2(group, fallDTO, true);
			GridData gd2 = new GridData(SWT.FILL, SWT.FILL, true, false, 1, 1);
			gd2.heightHint = 340;
			fallDetailBlatt2.setLayoutData(gd2);
		}

		@Override
		public void setUnlocked(boolean unlocked) {
			if (fallDetailBlatt2 != null) {
				fallDetailBlatt2.setUnlocked(unlocked);
			}
		}
	}

	class InvoiceContentMiddleComposite extends Composite implements IUnlockable {

		private List<IUnlockable> unlockables = new ArrayList<>();
		private List<IAction> actions = new ArrayList<>();

		public InvoiceContentMiddleComposite(Composite parent) {
			super(parent, SWT.NONE);
			GridLayout gd = new GridLayout(1, false);
			gd.marginWidth = 0;
			gd.marginHeight = 5;
			setLayout(gd);
			setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false, 1, 1));
			unlockables.clear();
			actions.clear();
		}

		public void createComponents(InvoiceCorrectionDTO invoiceCorrectionDTO) {
			FormToolkit tk = UiDesk.getToolkit();
			for (KonsultationDTO konsultationDTO : invoiceCorrectionDTO.getKonsultationDTOs()) {
				ScrolledForm form = tk.createScrolledForm(this);
				form.setBackground(UiDesk.getColor(UiDesk.COL_LIGHTGREY));
				form.setLayoutData(SWTHelper.getFillGridData(1, true, 1, false));
				Composite body = form.getBody();
				GridLayout gd1 = new GridLayout();
				gd1.marginWidth = 1;
				gd1.marginHeight = 1;
				body.setLayout(gd1);
				ExpandableComposite expandable = WidgetFactory.createExpandableComposite(tk, form, StringUtils.EMPTY);
				expandable.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));
				expandable.setExpanded(false);
				expandable.addExpansionListener(new ExpansionAdapter() {

					@Override
					public void expansionStateChanged(ExpansionEvent e) {

						invoiceComposite.updateScrollBars();

						if ((boolean) e.data) {
							Konsultation originKons = Konsultation.load(konsultationDTO.getId());
							contextService.setTyped(NoPoUtil.loadAsIdentifiable(originKons, IEncounter.class));
						} else {
							contextService.removeTyped(IEncounter.class);
						}
					}
				});
				Composite group = tk.createComposite(expandable, SWT.NONE);

				GridLayout gd = new GridLayout(2, false);
				gd.marginWidth = 0;
				gd.marginHeight = 0;
				group.setLayout(gd);
				group.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));
				expandable.setClient(group);
				updateKonsTitleText(expandable, konsultationDTO);

				ToolBarManager tbManager = new ToolBarManager(SWT.FLAT | SWT.HORIZONTAL | SWT.WRAP);
				IAction actionDateChange = new Action("Datum ändern") {

					@Override
					public ImageDescriptor getImageDescriptor() {
						return Images.IMG_CALENDAR.getImageDescriptor();
					}

					@Override
					public String getToolTipText() {
						return "Datum ändern";
					}

					@Override
					public void run() {
						DateSelectorDialog dlg = new DateSelectorDialog(getShell(),
								new TimeTool(konsultationDTO.getDate()));
						if (dlg.open() == Dialog.OK) {
							TimeTool date = dlg.getSelectedDate();
							String newDate = date.toString(TimeTool.DATE_GER);
							if (!StringUtils.equals(newDate, konsultationDTO.getDate())) {
								konsultationDTO.setDate(newDate);
								invoiceCorrectionDTO.addToCache(new InvoiceHistoryEntryDTO(
										OperationType.KONSULTATION_CHANGE_DATE, konsultationDTO, null));
								updateKonsTitleText(expandable, konsultationDTO);
							}
						}
					}
				};
				IAction actionMandantChange = new Action() {
					@Override
					public String getText() {
						return "Mandant ändern";
					}

					@Override
					public ImageDescriptor getImageDescriptor() {
						return Images.IMG_MANN.getImageDescriptor();
					}

					@Override
					public void run() {
						KontaktSelektor ksl = new KontaktSelektor(
								PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell(), Mandant.class,
								"Mandant auswählen", "Auf wen soll diese Kons verrechnet werden?",
								new String[] { Mandant.FLD_SHORT_LABEL, Mandant.FLD_NAME1, Mandant.FLD_NAME2 });
						if (ksl.open() == Dialog.OK) {
							Mandant srcMandant = konsultationDTO.getMandant();
							Mandant selectedMandant = (Mandant) ksl.getSelection();
							if (selectedMandant != null) {
								Rechnungssteller dstRechnungsteller = selectedMandant.getRechnungssteller();
								if (srcMandant != null) {
									Rechnungssteller srcRechnungsteller = srcMandant.getRechnungssteller();
									if (!StringUtils.equals(srcRechnungsteller.getId(), dstRechnungsteller.getId())) {

										if (!MessageDialog.openQuestion(getShell(), "Rechnungskorrektur",
												"Der Rechnungsteller des ausgewählten Mandants ist "
														+ dstRechnungsteller.getLabel()
														+ ".\nDieser unterscheidet sich zu dem bisherigen Rechnungsteller "
														+ srcRechnungsteller.getLabel()
														+ ".\n\nWollen Sie trotzdem den Mandanten "
														+ selectedMandant.getLabel() + " auswählen ?")) {
											return;

										}

									}
								}
								konsultationDTO.setMandant(selectedMandant);
								invoiceCorrectionDTO.addToCache(new InvoiceHistoryEntryDTO(
										OperationType.KONSULTATION_CHANGE_MANDANT, konsultationDTO, null));
								updateKonsTitleText(expandable, konsultationDTO);
							} else {
								MessageDialog.openWarning(getShell(), "Rechnungskorrektur",
										"Mandantenauswahl fehlerhaft. Der Mandant konnte nicht geändert werden.");
							}
						}
					}
				};
				IAction transferKons = new Action("Konsultation Transfer") {

					@Override
					public ImageDescriptor getImageDescriptor() {
						return Images.IMG_MOVETOLOWERLIST.getImageDescriptor();
					}

					@Override
					public String getToolTipText() {
						return "Konsultation auf anderen Fall verschieben";
					}

					@Override
					public void run() {

						FallSelectionDialog fallSelectionDialog = new FallSelectionDialog(getShell(),
								"Bitte wählen Sie einen Fall aus, auf das die u.a. Konsultation transferiert werden soll.\n"
										+ "Konsultation: " + konsultationDTO.getDate(),
								invoiceCorrectionDTO.getFallDTO());
						if (fallSelectionDialog.open() == MessageDialog.OK) {
							if (fallSelectionDialog.getSelectedFall().isPresent()) {
								if (invoiceCorrectionDTO.getKonsultationDTOs().remove(konsultationDTO)) {
									IFall iFall = fallSelectionDialog.getSelectedFall().get();
									invoiceCorrectionDTO.addToCache(new InvoiceHistoryEntryDTO(
											OperationType.KONSULTATION_TRANSFER_TO_FALL, konsultationDTO, iFall));
									form.dispose();
									layout(true, true);
									invoiceComposite.updateScrollBars();
								}
							}

						}
					}
				};
				tbManager.add(actionDateChange);
				tbManager.add(actionMandantChange);
				tbManager.add(transferKons);

				actions.add(actionDateChange);
				actions.add(actionMandantChange);
				ToolBar toolbar = tbManager.createControl(group);
				// align toolbar right
				GridDataFactory.fillDefaults().align(SWT.END, SWT.CENTER).grab(true, false).applyTo(toolbar);
				InvoiceContentDiagnosisComposite invoiceContentDiagnosisComposite = new InvoiceContentDiagnosisComposite(
						group);
				InvoiceContentKonsultationComposite invoiceContentKonsultationComposite = new InvoiceContentKonsultationComposite(
						group);

				invoiceContentDiagnosisComposite.createComponents(konsultationDTO);
				invoiceContentKonsultationComposite.createComponents(konsultationDTO);

				unlockables.add(invoiceContentDiagnosisComposite);
				unlockables.add(invoiceContentKonsultationComposite);
			}

		}

		public void updateKonsTitleText(ExpandableComposite expandableComposite, KonsultationDTO konsultationDTO) {
			expandableComposite.setText("Konsultation: " + konsultationDTO.getDate() + " Mandant: "
					+ konsultationDTO.getMandant().getLabel());
		}

		@Override
		public void setUnlocked(boolean unlocked) {
			for (IUnlockable iUnlockable : unlockables) {
				iUnlockable.setUnlocked(unlocked);
			}
			for (IAction action : actions) {
				action.setEnabled(unlocked);
			}

		}
	}

	class InvoiceContentKonsultationComposite extends Composite implements IUnlockable {
		TableViewer tableViewer;
		TableColumnLayout tableColumnLayout;

		public InvoiceContentKonsultationComposite(Composite parent) {
			super(parent, SWT.NONE);
			GridLayout gd = new GridLayout(1, false);
			gd.marginWidth = 0;
			gd.marginHeight = 0;
			setLayout(gd);
			setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 2, 2));
		}

		private void createComponents(KonsultationDTO konsultationDTO) {
			Composite tableArea = new Composite(this, SWT.NONE);
			tableArea.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));
			tableColumnLayout = new TableColumnLayout();
			tableArea.setLayout(tableColumnLayout);

			tableViewer = new TableViewer(tableArea, SWT.BORDER | SWT.FULL_SELECTION);
			ColumnViewerToolTipSupport.enableFor(tableViewer, ToolTip.NO_RECREATE);
			Table table = tableViewer.getTable();

			table.setHeaderVisible(true);
			table.setLinesVisible(true);

			createTableViewerColumn("Anzahl", 1, 0);
			createTableViewerColumn("Leistungscode", 4, 1);
			createTableViewerColumn("Leistungstext", 12, 2);
			createTableViewerColumn("Preis", 3, 3);

			tableViewer.setContentProvider(new ArrayContentProvider());
			tableViewer.setInput(konsultationDTO.getLeistungDTOs());
			tableViewer.setComparator(new ViewerComparator() {
				@Override
				public int compare(Viewer viewer, Object e1, Object e2) {
					return ObjectUtils.compare(((LeistungDTO) e1).getLastUpdate(), ((LeistungDTO) e2).getLastUpdate());
				}
			});

			invoiceCorrectionDTO.getFallDTO().register(new IFallChanged() {

				@Override
				public void changed(FallDTO fallDTO, boolean triggersRecalc) {
					if (triggersRecalc) {
						for (KonsultationDTO konsultationDTO : invoiceCorrectionDTO.getKonsultationDTOs()) {
							for (LeistungDTO leistungDTO : konsultationDTO.getLeistungDTOs()) {
								leistungDTO.calcPrice(konsultationDTO, fallDTO, new ShowBilledResult());
							}
						}
					}
					if (invoiceBottomComposite != null) {
						invoiceBottomComposite.refresh(true);
					}
					if (!isDisposed()) {
						tableViewer.refresh();
					}
				}

			});

			GenericObjectDropTarget.IReceiver dtr = new GenericObjectDropTarget.IReceiver() {

				@Override
				public void dropped(List<Object> list, DropTargetEvent e) {
					for (Object object : list) {
						if (object instanceof IBillable) {
							LeistungDTO leistungDTO = new LeistungDTO((IBillable) object,
									invoiceCorrectionDTO.getFallDTO());

								konsultationDTO.getLeistungDTOs().add(leistungDTO);
							if (leistungDTO.calcPrice(konsultationDTO, invoiceCorrectionDTO.getFallDTO(),
									new ShowBilledResult())) {
								invoiceCorrectionDTO.addToCache(new InvoiceHistoryEntryDTO(OperationType.LEISTUNG_ADD,
										konsultationDTO, leistungDTO));
								tableViewer.refresh();
								invoiceComposite.updateScrollBars();
							} else {
								konsultationDTO.getLeistungDTOs().remove(leistungDTO);
							}
						}
					}
				}

				@Override
				public boolean accept(List<Object> list) {
					return true;
				}

			};

			GenericObjectDropTarget dropTarget = new GenericObjectDropTarget("rechnungskorrektur", table, dtr) {
				@Override
				protected Control getHighLightControl() {
					return tableViewer.getControl();
				}
			};

			MenuManager menuManager = new MenuManager();

			menuManager.add(new Action() {
				@Override
				public String getText() {
					return "Anzahl ändern";
				}

				@Override
				public ImageDescriptor getImageDescriptor() {
					return null;
				}

				@Override
				public void run() {
					LeistungDTO leistungDTO = getSelection();
					if (leistungDTO != null && changeQuantityDialog(leistungDTO)) {
						invoiceCorrectionDTO.addToCache(new InvoiceHistoryEntryDTO(OperationType.LEISTUNG_CHANGE_COUNT,
								konsultationDTO, leistungDTO));
						tableViewer.refresh();

					}
				}
			});
			menuManager.add(new Action() {
				@Override
				public String getText() {
					return "Preis ändern";
				}

				@Override
				public ImageDescriptor getImageDescriptor() {
					return null;
				}

				@Override
				public void run() {
					LeistungDTO leistungDTO = getSelection();
					if (leistungDTO != null && changePriceDialog(leistungDTO)) {
						invoiceCorrectionDTO.addToCache(new InvoiceHistoryEntryDTO(OperationType.LEISTUNG_CHANGE_PRICE,
								konsultationDTO, leistungDTO));
						tableViewer.refresh();
					}
				}
			});
			menuManager.add(new Separator(IWorkbenchActionConstants.MB_ADDITIONS));
			menuManager.add(new Action() {
				@Override
				public String getText() {
					return "Leistung auf neuen Fall/Kons transferieren";
				}

				@Override
				public ImageDescriptor getImageDescriptor() {
					return null;
				}

				@Override
				public void run() {
					LeistungDTO leistungDTO = getSelection();
					if (leistungDTO != null) {
						FallSelectionDialog fallSelectionDialog = new FallSelectionDialog(getShell(),
								"Bitte wählen Sie einen Fall aus, auf das die u.a. Leistung transferiert werden soll.\n"
										+ leistungDTO.getText(),
								invoiceCorrectionDTO.getFallDTO());
						if (fallSelectionDialog.open() == MessageDialog.OK) {
							if (fallSelectionDialog.getSelectedFall().isPresent()) {
								konsultationDTO.getLeistungDTOs().remove(leistungDTO);
								IFall iFall = fallSelectionDialog.getSelectedFall().get();
								InvoiceHistoryEntryDTO existingEntry = invoiceCorrectionDTO
										.getHistoryEntryForLeistungTransferFromCache(iFall);

								if (existingEntry != null && existingEntry.getItem() instanceof List<?>) {
									@SuppressWarnings("unchecked")
									List<LeistungDTO> leistungen = (List<LeistungDTO>) existingEntry.getItem();
									leistungen.add(leistungDTO);
								} else {
									List<LeistungDTO> leistungen = new ArrayList<>();
									leistungen.add(leistungDTO);
									invoiceCorrectionDTO.addToCache(
											new InvoiceHistoryEntryDTO(OperationType.LEISTUNG_TRANSFER_TO_FALL_KONS,
													konsultationDTO, leistungen, iFall));

								}
								tableViewer.refresh();
								invoiceComposite.updateScrollBars();
							}

						}
					}
				}
			});
			menuManager.add(new Separator(IWorkbenchActionConstants.MB_ADDITIONS));
			menuManager.add(new Action() {
				@Override
				public String getText() {
					return "Leistung hinzufügen";
				}

				@Override
				public ImageDescriptor getImageDescriptor() {
					return null;
				}

				@Override
				public void run() {

					try {
						getSite().getPage().showView(LeistungenView.ID);
						CodeSelectorHandler.getInstance().setCodeSelectorTarget(dropTarget);
					} catch (PartInitException e) {
						LoggerFactory.getLogger(InvoiceCorrectionDTO.class).error("cannot init leistungen viewpart", e); //$NON-NLS-1$
					}
				}
			});
			menuManager.add(new Action() {
				@Override
				public String getText() {
					return "Leistung entfernen";
				}

				@Override
				public ImageDescriptor getImageDescriptor() {
					return null;
				}

				@Override
				public void run() {
					LeistungDTO leistungDTO = getSelection();
					if (leistungDTO != null) {
						konsultationDTO.getLeistungDTOs().remove(leistungDTO);
						invoiceCorrectionDTO.addToCache(new InvoiceHistoryEntryDTO(OperationType.LEISTUNG_REMOVE,
								konsultationDTO, leistungDTO));
						tableViewer.refresh();
						invoiceComposite.updateScrollBars();
					}
				}
			});

			tableViewer.getTable().setMenu(menuManager.createContextMenu(tableViewer.getTable()));

		}

		public LeistungDTO getSelection() {
			if (tableViewer != null) {
				StructuredSelection structuredSelection = (StructuredSelection) tableViewer.getSelection();
				if (!structuredSelection.isEmpty()) {
					return (LeistungDTO) structuredSelection.getFirstElement();
				}
			}
			return null;
		}

		private TableViewerColumn createTableViewerColumn(String title, int bound, int colIdx) {
			final TableViewerColumn viewerColumn = new TableViewerColumn(tableViewer, SWT.NONE);
			TableColumn column = viewerColumn.getColumn();
			column.setText(title);
			tableColumnLayout.setColumnData(column, new ColumnWeightData(bound, ColumnWeightData.MINIMUM_WIDTH, true));
			column.setResizable(true);
			column.setMoveable(false);
			viewerColumn.setLabelProvider(new DefaultColumnLabelProvider(colIdx));
			return viewerColumn;
		}

		private class DefaultColumnLabelProvider extends ColumnLabelProvider {
			int colIdx;

			public DefaultColumnLabelProvider(int colIdx) {
				this.colIdx = colIdx;
			}

			@Override
			public String getText(Object element) {
				LeistungDTO leistungDTO = (LeistungDTO) element;
				switch (colIdx) {
				case 0:
					return String.valueOf(leistungDTO.getCount());
				case 1:
					return leistungDTO.getCode();
				case 2:
					return leistungDTO.getText();
				case 3:
					return leistungDTO.getPrice() != null ? leistungDTO.getPrice().getAmountAsString() : "0"; //$NON-NLS-1$
				default:
					return StringUtils.EMPTY;
				}

			}
		}

		@Override
		public void setUnlocked(boolean unlocked) {
			if (tableViewer != null) {
				tableViewer.getTable().setEnabled(unlocked);
			}
		}
	}

	class InvoiceContentDiagnosisComposite extends Composite implements IUnlockable {

		TableViewer tableViewer;
		TableColumnLayout tableColumnLayout;

		public InvoiceContentDiagnosisComposite(Composite parent) {
			super(parent, SWT.NONE);
			GridLayout gd = new GridLayout(1, false);
			gd.marginWidth = 0;
			gd.marginHeight = 0;
			setLayout(gd);
			setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false, 2, 1));
		}

		private void createComponents(KonsultationDTO konsultationDTO) {
			Composite tableArea = new Composite(this, SWT.NONE);
			tableArea.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false, 1, 1));
			tableColumnLayout = new TableColumnLayout();
			tableArea.setLayout(tableColumnLayout);

			tableViewer = new TableViewer(tableArea, SWT.BORDER | SWT.FULL_SELECTION);
			ColumnViewerToolTipSupport.enableFor(tableViewer, ToolTip.NO_RECREATE);
			Table table = tableViewer.getTable();
			table.setLayoutData(SWTHelper.getFillGridData(1, true, 1, true));

			createTableViewerColumn(Messages.DiagnosenDisplay_Diagnoses, 1, 0);

			table.setHeaderVisible(true);
			table.setLinesVisible(true);

			GenericObjectDropTarget.IReceiver dtr = new GenericObjectDropTarget.IReceiver() {

				@Override
				public void dropped(List<Object> list, DropTargetEvent e) {
					for (Object object : list) {
						if (object instanceof IDiagnosis) {
							IDiagnosis art = (IDiagnosis) object;
							DiagnosesDTO dto = new DiagnosesDTO(art);
							konsultationDTO.getDiagnosesDTOs().add(dto);
							invoiceCorrectionDTO.addToCache(
									new InvoiceHistoryEntryDTO(OperationType.DIAGNOSE_ADD, konsultationDTO, dto));
							tableViewer.refresh();
							invoiceComposite.updateScrollBars();
						}
					}
				}

				@Override
				public boolean accept(List<Object> list) {
					return true;
				}

			};

			GenericObjectDropTarget dropTarget = new GenericObjectDropTarget("rechnungskorrekturBehandlungen", table, //$NON-NLS-1$
					dtr) {
				@Override
				protected Control getHighLightControl() {
					return tableViewer.getControl();
				}
			};

			MenuManager menuManager = new MenuManager();
			menuManager.add(new Action() {
				@Override
				public String getText() {
					return Messages.DiagnosenDisplay_AddDiagnosis;
				}

				@Override
				public ImageDescriptor getImageDescriptor() {
					return null;
				}

				@Override
				public void run() {

					try {
						getSite().getPage().showView(DiagnosenView.ID);
						CodeSelectorHandler.getInstance().setCodeSelectorTarget(dropTarget);
					} catch (PartInitException e) {
						LoggerFactory.getLogger(InvoiceCorrectionDTO.class).error("cannot init diagnose viewpart", e); //$NON-NLS-1$
					}
				}
			});
			menuManager.add(new Action() {
				@Override
				public String getText() {
					return "Diagnose entfernen";
				}

				@Override
				public ImageDescriptor getImageDescriptor() {
					return null;
				}

				@Override
				public void run() {
					DiagnosesDTO dto = getSelection();
					if (dto != null) {
						konsultationDTO.getDiagnosesDTOs().remove(dto);
						invoiceCorrectionDTO.addToCache(
								new InvoiceHistoryEntryDTO(OperationType.DIAGNOSE_REMOVE, konsultationDTO, dto));
						tableViewer.refresh();
						invoiceComposite.updateScrollBars();
					}
				}
			});

			tableViewer.getTable().setMenu(menuManager.createContextMenu(tableViewer.getTable()));

			tableViewer.setContentProvider(new ArrayContentProvider());
			tableViewer.setInput(konsultationDTO.getDiagnosesDTOs());
		}

		public DiagnosesDTO getSelection() {
			if (tableViewer != null) {
				StructuredSelection structuredSelection = (StructuredSelection) tableViewer.getSelection();
				if (!structuredSelection.isEmpty()) {
					return (DiagnosesDTO) structuredSelection.getFirstElement();
				}
			}
			return null;
		}

		private TableViewerColumn createTableViewerColumn(String title, int bound, int colIdx) {
			final TableViewerColumn viewerColumn = new TableViewerColumn(tableViewer, SWT.NONE);
			TableColumn column = viewerColumn.getColumn();
			column.setText(title);
			tableColumnLayout.setColumnData(column, new ColumnWeightData(bound, ColumnWeightData.MINIMUM_WIDTH, true));
			column.setResizable(true);
			column.setMoveable(false);
			viewerColumn.setLabelProvider(new DefaultColumnLabelProvider(colIdx));
			return viewerColumn;
		}

		private class DefaultColumnLabelProvider extends ColumnLabelProvider {
			int colIdx;

			public DefaultColumnLabelProvider(int colIdx) {
				this.colIdx = colIdx;
			}

			@Override
			public String getText(Object element) {
				DiagnosesDTO diagnosesDTO = (DiagnosesDTO) element;
				switch (colIdx) {
				case 0:
					return diagnosesDTO.getLabel();
				default:
					return StringUtils.EMPTY;
				}
			}
		}

		@Override
		public void setUnlocked(boolean unlocked) {
			if (tableViewer != null) {
				tableViewer.getTable().setEnabled(unlocked);
			}
		}
	}

	class InvoiceBottomComposite extends Composite implements IUnlockable {

		Button btnCancel;
		Button btnCorrection;

		public InvoiceBottomComposite(Composite parent) {
			super(parent, SWT.NONE);
			GridLayout gd = new GridLayout(1, false);
			gd.marginWidth = 0;
			gd.marginHeight = 0;
			setLayout(gd);
			setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));
		}

		public void createComponents() {
			Composite parent = new Composite(this, SWT.NONE);
			GridLayout gd = new GridLayout(3, false);
			gd.marginWidth = 0;
			gd.marginHeight = 2;
			parent.setLayout(gd);
			parent.setLayoutData(new GridData(SWT.RIGHT, SWT.TOP, true, true, 1, 1));

			btnCorrection = new Button(parent, SWT.NONE);
			btnCorrection.setEnabled(false);
			btnCorrection.setText("Rechnungskorrektur starten..");
			btnCorrection.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					Result<String> res = doBillCorrection(actualInvoice);
					if (res != null) {
						if (SEVERITY.ERROR.equals(res.getSeverity())) {
							MessageDialog.openError(Display.getDefault().getActiveShell(), "Rechnungskorrektur",
									res.get());
						}

						// auto open new invoice nr
						if (invoiceCorrectionDTO.getNewInvoiceNumber() != null
								&& invoiceCorrectionDTO.isOpenNewInvoice()) {
							openInvoiceNr(invoiceCorrectionDTO.getNewInvoiceNumber());
						} else {
							// reloads the current invoice nr
							contextService.postEvent(ElexisEventTopics.EVENT_UPDATE,
									NoPoUtil.loadAsIdentifiable(actualInvoice, IInvoice.class));
						}
					}
				}
			});

			btnCancel = new Button(parent, SWT.NONE);
			btnCancel.setText("Zurücksetzen");
			btnCancel.setEnabled(false);
			btnCancel.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					log.debug("invoice correction: invoice reset"); //$NON-NLS-1$
					reload(actualInvoice);
					MessageDialog.openInformation(Display.getDefault().getActiveShell(), "Rechnungskorrektur",
							"Die Rechnung wurde erfolgreich zurückgesetzt.");
				}
			});

			this.setVisible(actualInvoice != null && actualInvoice.isCorrectable());
		}

		public void refresh(boolean hasChanges) {
			if (invoiceCorrectionDTO != null) {
				if (btnCancel != null) {
					btnCancel.setEnabled(hasChanges && isUnlocked());
				}
				if (btnCorrection != null) {
					btnCorrection.setEnabled(hasChanges && isUnlocked());
				}
			}
		}

		@Override
		public void setUnlocked(boolean unlocked) {
			refresh(invoiceCorrectionDTO.hasChanges());
		}
	}

	/**
	 * Copies the actual fall, merge the copied fall with changes, transfer cons,
	 * storno the old invoice
	 */
	private Result<String> doBillCorrection(Rechnung actualInvoice) {
		log.debug("invoice correction: start"); //$NON-NLS-1$
		if (actualInvoice != null && actualInvoice.isCorrectable()) {
			if (actualInvoice.getFall() != null && invoiceCorrectionDTO != null
					&& invoiceCorrectionDTO.getFallDTO() != null) {
				try {
					log.debug("invoice correction: invoice number [{}]", actualInvoice.getNr()); //$NON-NLS-1$
					invoiceCorrectionDTO.updateHistory();

					InvoiceCorrectionWizardDialog wizardDialog = new InvoiceCorrectionWizardDialog(getSite().getShell(),
							invoiceCorrectionDTO);
					wizardDialog.addPageChangedListener(new IPageChangedListener() {

						@Override
						public void pageChanged(PageChangedEvent event) {

							if (event.getSelectedPage() instanceof Page2) {
								log.debug("invoice correction: processable changes {}", //$NON-NLS-1$
										invoiceCorrectionDTO.getHistory().stream().map(item -> item.getOperationType())
												.collect(Collectors.toList()));
								Page2 page = (Page2) event.getSelectedPage();
								InvoiceCorrectionDTO invoiceCorrectionDTO = page.getInvoiceCorrectionDTO();

								BillingUtil.doBillCorrection(invoiceCorrectionDTO, new BillCallback() {

									@Override
									public List<Konsultation> storno(Rechnung rechnung) {
										RnDialogs.StornoDialog stronoDlg = new RnDialogs.StornoDialog(
												UiDesk.getDisplay().getActiveShell(), rechnung, true);
										if (stronoDlg.openDialog() == Dialog.OK) {
											return stronoDlg.getKonsultations();
										} else {
											page.getTxtOutput().setText(
													"Die Rechnungskorrektur wurde durch den Benutzer abgebrochen.");
											return null;
										}
									}
								});

								page.updateProcess();
								if (invoiceCorrectionDTO.getOutputText() != null) {
									page.getTxtOutput().setText(invoiceCorrectionDTO.getOutputText());
								}
							}
						}

					});

					wizardDialog.open();
					if (invoiceCorrectionDTO.getOutputText() != null) {

						setInvoiceCorrectionInfo(actualInvoice);

						if (invoiceCorrectionDTO.isCorrectionSuccess()) {
							// set bemerkung text
							StringBuilder txtBemerkung = new StringBuilder();
							if (txtBemerkung != null) {
								txtBemerkung.append(actualInvoice.getBemerkung());
							}
							if (txtBemerkung.length() > 0) {
								txtBemerkung.append(StringUtils.LF);
							}
							txtBemerkung.append(invoiceCorrectionDTO.getOutputText());
							actualInvoice.setBemerkung(txtBemerkung.toString());

							log.debug("invoice correction: successfully finished"); //$NON-NLS-1$
							return new Result<>("ok");
						}
						log.debug("invoice correction: failed with warnings"); //$NON-NLS-1$
						return new Result<>(SEVERITY.WARNING, 2, "warn", null, false); //$NON-NLS-1$
					}
				} catch (Exception e) {
					log.error("invoice correction: failed with errors [{}]", actualInvoice.getId(), e); //$NON-NLS-1$
					setInvoiceCorrectionInfo(actualInvoice);
					return new Result<>(SEVERITY.ERROR, 2, "error", //$NON-NLS-1$
							"Die Rechnungskorrektur konnte nicht vollständig durchgeführt werden.\nFür mehr Details, beachten Sie bitte das Log-File.",
							false);
				}
			}
		}
		return null;
	}

	private void setInvoiceCorrectionInfo(Rechnung actualInvoice) {
		if (actualInvoice != null && invoiceCorrectionDTO != null) {

			actualInvoice.setExtInfoStoredObjectByKey(Rechnung.INVOICE_CORRECTION,
					StringUtils.isEmpty(invoiceCorrectionDTO.getNewInvoiceNumber()) ? StringUtils.EMPTY
							: invoiceCorrectionDTO.getNewInvoiceNumber());
		}
	}

	private boolean changePriceDialog(LeistungDTO leistungDTO) {
		Money oldPrice = leistungDTO.getPrice();
		String p = oldPrice.getAmountAsString();
		Money customPrice;
		InputDialog dlg = new InputDialog(UiDesk.getTopShell(), Messages.VerrechnungsDisplay_changePriceForService, // $NON-NLS-1$
				Messages.VerrechnungsDisplay_enterNewPrice, p, // $NON-NLS-1$
				null);
		if (dlg.open() == Dialog.OK) {
			try {
				String val = dlg.getValue().trim();
				if (val.endsWith("%") && val.length() > 1) { //$NON-NLS-1$
					val = val.substring(0, val.length() - 1);
					double percent = Double.parseDouble(val);
					double scaleFactor = 1.0 + (percent / 100.0);
					customPrice = leistungDTO.getPrice().multiply(scaleFactor);
				} else {
					customPrice = new Money(val);
				}
				if (customPrice != null) {
					leistungDTO.setTp(customPrice.getCents());
				}
				return true;
			} catch (ParseException ex) {
				log.error("price changing", ex); //$NON-NLS-1$
				SWTHelper.showError(Messages.VerrechnungsDisplay_badAmountCaption, // $NON-NLS-1$
						Messages.VerrechnungsDisplay_badAmountBody); // $NON-NLS-1$
			}
		}
		return false;
	}

	private boolean changeQuantityDialog(LeistungDTO leistungDTO) {
		String p = Double.toString(leistungDTO.getCount());
		InputDialog dlg = new InputDialog(UiDesk.getTopShell(), Messages.VerrechnungsDisplay_changeNumberCaption, // $NON-NLS-1$
				Messages.VerrechnungsDisplay_changeNumberBody, // $NON-NLS-1$
				p, null);
		if (dlg.open() == Dialog.OK) {
			try {
				String val = dlg.getValue();
				if (!StringTool.isNothing(val)) {
					double changeAnzahl = 1.0;
					String text = leistungDTO.getIVerrechenbar().getText();

					if (val.indexOf(StringConstants.SLASH) > 0) {
						String[] frac = val.split(StringConstants.SLASH);
						changeAnzahl = Double.parseDouble(frac[0]) / Double.parseDouble(frac[1]);
						text = leistungDTO.getIVerrechenbar().getText() + " (" + val //$NON-NLS-1$
								+ Messages.VerrechnungsDisplay_Orininalpackungen;
					} else if (val.indexOf('.') > 0) {
						changeAnzahl = Double.parseDouble(val);
						text = leistungDTO.getIVerrechenbar().getText() + " (" + Double.toString(changeAnzahl) //$NON-NLS-1$
								+ ")"; //$NON-NLS-1$
					} else {
						changeAnzahl = Double.parseDouble(val);
					}

					leistungDTO.setCount(changeAnzahl);
					return true;
				}
			} catch (NumberFormatException ne) {
				log.error("quantity changing", ne); //$NON-NLS-1$
				SWTHelper.showError(Messages.VerrechnungsDisplay_invalidEntryCaption, // $NON-NLS-1$
						Messages.VerrechnungsDisplay_invalidEntryBody); // $NON-NLS-1$
			}
		}

		return false;
	}

	private void releaseAndRefreshLock(IPersistentObject object, String commandId) {
		if (object != null && LocalLockServiceHolder.get().isLocked(object)) {
			LocalLockServiceHolder.get().releaseLock(object);
		}
		ICommandService commandService = PlatformUI.getWorkbench().getService(ICommandService.class);
		commandService.refreshElements(commandId, null);
	}

	@Override
	public void setUnlocked(boolean unlocked) {

		this.unlocked = unlocked && actualInvoice != null && actualInvoice.isCorrectable();

		if (invoiceComposite != null) {
			invoiceComposite.setUnlocked(this.unlocked);
		}
	}

	public boolean isUnlocked() {
		return unlocked;
	}

	@Optional
	@Inject
	public void setFixLayout(MPart part, @Named(Preferences.USR_FIX_LAYOUT) boolean currentState) {
		CoreUiUtil.updateFixLayout(part, currentState);
	}

	private static class ShowBilledResult implements Consumer<Result<IBilled>> {

		@Override
		public void accept(Result<IBilled> result) {
			Display.getDefault().syncExec(() -> {
				ResultDialog.show(result);
			});
		}
	}
}