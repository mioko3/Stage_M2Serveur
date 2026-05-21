package app.ihm.dialogue;

import app.IControleur;
import app.ihm.FenetrePrincipale;
import app.ihm.IhmUtils;
import app.ihm.gestionlot.PanelSocietes;
import app.metier.personelle.Ace;
import app.metier.personelle.Societe;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.table.DefaultTableModel;

public class DialogEditSociete extends JDialog
{
	private final FenetrePrincipale fenetre;
	private final IControleur       ctrl;
	private final Societe           soc;
	private final PanelSocietes     panelSoc;

	private JTextField        fNom, fCe, fHeures, fEffect;
	private DefaultTableModel modelAces;
	private JTable            tblAces;
	private JLabel            lblErreur;

	public DialogEditSociete(FenetrePrincipale fenetre, IControleur ctrl,
	                         Societe soc, PanelSocietes panelSoc)
	{
		super(fenetre, "Modifier — " + soc.getNom(), true);
		this.fenetre  = fenetre;
		this.ctrl     = ctrl;
		this.soc      = soc;
		this.panelSoc = panelSoc;
		setSize(580, 480);
		setLocationRelativeTo(fenetre);
		setLayout(new BorderLayout(0, 6));
		add(creerFormulaire(),  BorderLayout.NORTH);
		add(creerTableauAces(), BorderLayout.CENTER);
		add(creerBas(),         BorderLayout.SOUTH);
		preRemplir();
	}

	private JPanel creerFormulaire()
	{
		fNom    = new JTextField();
		fNom.setFocusable(false);
		fCe     = new JTextField();
		fHeures = new JTextField();
		fEffect = new JTextField();

		JPanel form = new JPanel(new GridBagLayout());
		form.setBorder(BorderFactory.createTitledBorder("Informations société"));
		form.setBackground(Color.WHITE);
		GridBagConstraints gc = new GridBagConstraints();
		gc.insets = new Insets(6, 8, 6, 8);
		gc.fill   = GridBagConstraints.HORIZONTAL;

		Object[][] champs = {
			{"Nom société",             fNom   },
			{"Responsable CE",          fCe    },
			{"Heures disponibles (CE)", fHeures},
			{"Effectif",                fEffect},
		};
		for (int i = 0; i < champs.length; i++)
		{
			gc.gridx = 0; gc.gridy = i; gc.weightx = 0.3;
			JLabel l = new JLabel((String) champs[i][0]);
			l.setFont(new Font("SansSerif", Font.PLAIN, 12));
			l.setForeground(Color.GRAY);
			form.add(l, gc);
			gc.gridx = 1; gc.weightx = 0.7;
			form.add((Component) champs[i][1], gc);
		}
		return form;
	}

	private JPanel creerTableauAces()
	{
		modelAces = new DefaultTableModel(
			new String[]{"Nom ACE", "Nb personnes", "Effectif actuel"}, 0)
		{
			public boolean isCellEditable(int r, int c) { return true; }
		};
		tblAces = IhmUtils.creerTable(modelAces);
		tblAces.setRowHeight(28);

		JButton btnAdd = IhmUtils.bouton("+ Ajouter une ACE",                 new Color(60, 140, 60), Color.WHITE);
		JButton btnDel = IhmUtils.bouton("− Supprimer la ligne sélectionnée", new Color(180, 30, 30), Color.WHITE);
		btnAdd.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
		btnDel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
		btnAdd.addActionListener(e -> modelAces.addRow(new Object[]{"Nouvelle ACE", 1, 1}));
		btnDel.addActionListener(e -> {
			int row = tblAces.getSelectedRow();
			if (row >= 0) modelAces.removeRow(row);
		});

		JPanel barre = new JPanel();
		barre.setLayout(new BoxLayout(barre, BoxLayout.X_AXIS));
		barre.setBackground(IhmUtils.FOND);
		barre.setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));
		barre.add(btnAdd);
		barre.add(Box.createHorizontalStrut(8));
		barre.add(btnDel);

		JPanel p = new JPanel(new BorderLayout(0, 4));
		p.setBorder(BorderFactory.createTitledBorder(
			"ACE  (modifiables directement dans le tableau)"));
		p.setBackground(Color.WHITE);
		p.add(new JScrollPane(tblAces), BorderLayout.CENTER);
		p.add(barre,                    BorderLayout.SOUTH);
		return p;
	}

	private JPanel creerBas()
	{
		lblErreur = new JLabel(" ");
		lblErreur.setForeground(IhmUtils.ROUGE);
		lblErreur.setFont(new Font("SansSerif", Font.ITALIC, 12));

		JButton btnOk  = IhmUtils.bouton("Enregistrer", IhmUtils.VERT,           Color.WHITE);
		JButton btnAnn = IhmUtils.bouton("Annuler",     new Color(100, 100, 100), Color.WHITE);
		btnAnn.addActionListener(e -> dispose());
		btnOk .addActionListener(e -> valider());

		JPanel p = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
		p.setBackground(Color.WHITE);
		p.add(lblErreur); p.add(btnAnn); p.add(btnOk);
		return p;
	}

	private void preRemplir()
	{
		fNom   .setText(soc.getNom() != null ? soc.getNom() : "");
		fCe    .setText(soc.getCe()  != null ? soc.getCe()  : "");
		fHeures.setText(String.valueOf(soc.getTotalHeuresCE()));
		fEffect.setText(String.valueOf(soc.getEffectifTotal()));
		for (Ace a : soc.getAces())
			modelAces.addRow(new Object[]{a.getNom(), a.getNbPers(), a.getEffectifActuel()});
	}

	private void valider()
	{
		try
		{
			if (tblAces.isEditing()) tblAces.getCellEditor().stopCellEditing();

			String nom = fNom.getText().trim();
			if (nom.isEmpty()) { lblErreur.setText("Le nom est obligatoire."); return; }
			String ce     = fCe.getText().trim();
			int    heures = Integer.parseInt(fHeures.getText().trim());
			int    effect = Integer.parseInt(fEffect.getText().trim());

			ctrl.modifierSociete(soc, nom, ce, heures, effect);

			java.util.ArrayList<Ace> aces = new java.util.ArrayList<>();
			for (int i = 0; i < modelAces.getRowCount(); i++)
			{
				String nomAce   = modelAces.getValueAt(i, 0).toString().trim();
				int    nbPers   = parseInt(modelAces.getValueAt(i, 1));
				int    effectif = parseInt(modelAces.getValueAt(i, 2));
				aces.add(new Ace(nomAce, nbPers, effectif));
			}

			if (!ctrl.mettreAJourAces(soc, aces))
			{
				lblErreur.setText("Impossible de supprimer une ACE avec des lots affectés.");
				return;
			}

			panelSoc.rafraichir();
			dispose();
		}
		catch (NumberFormatException ex)
		{
			lblErreur.setText("Valeur invalide : " + ex.getMessage());
		}
		this.fenetre.rafraichirTout();
	}

	private int parseInt(Object v)
	{
		try { return Integer.parseInt(v.toString().trim()); }
		catch (NumberFormatException e) { return 0; }
	}
}