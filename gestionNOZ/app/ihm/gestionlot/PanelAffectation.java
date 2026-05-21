package app.ihm.gestionlot;

import app.Controleur;
import app.ihm.FenetrePrincipale;
import app.ihm.IhmUtils;
import app.ihm.dialogue.DialogAjoutLot;
import app.ihm.dialogue.DialogEditLot;
import app.metier.lot.Lot;
import app.metier.personelle.Ace;
import app.metier.personelle.Societe;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.Timer;
import javax.swing.table.DefaultTableModel;

/**
 * Onglet "Affectation des lots".
 *
 * Modifications issues de livraison :
 *   - lotsDisponiblesAffiches / lotsAffectesAffiches : listes de référence
 *     pour getLotSelectionne() — plus de compteur fragile dans une boucle
 *   - combFiltreStatut intégré dans le tableau gauche (sous la recherche)
 */
public class PanelAffectation extends JPanel
{
	private final Controleur       ctrl;
	private final FenetrePrincipale fenetre;

	// ── Listes de référence (cf. livraison) ───────────────────────────────
	private List<Lot> lotsDisponiblesAffiches = new ArrayList<>();
	private List<Lot> lotsAffectesAffiches    = new ArrayList<>();

	// ── Tableau gauche : lots disponibles ─────────────────────────────────
	private DefaultTableModel modelDisponibles;
	private JTable            tblDisponibles;
	private JTextField        txtRechercheDisp;
	private JComboBox<String> combFiltreStatut;

	// ── Panneau central ───────────────────────────────────────────────────
	private JTextArea         infoLot;
	private JComboBox<String> combSociete;
	private JComboBox<String> combAce;
	private JLabel            lblStatut;

	// ── Tableau droit : lots affectés ─────────────────────────────────────
	private DefaultTableModel modelAffectes;
	private JTable            tblAffectes;
	private JTextField        txtRechercheAff;

	public PanelAffectation(Controleur ctrl, FenetrePrincipale fenetre)
	{
		this.ctrl    = ctrl;
		this.fenetre = fenetre;
		setLayout(new BorderLayout(8, 8));
		setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
		setBackground(IhmUtils.FOND);

		add(creerTableauDisponibles(), BorderLayout.WEST);
		add(creerPanelCentral(),       BorderLayout.CENTER);
		add(creerTableauAffectes(),    BorderLayout.EAST);
	}

	// ── Tableau gauche ────────────────────────────────────────────────────

	private JPanel creerTableauDisponibles()
	{
		// Colonne "Nb pcs" ajoutée (cf. livraison)
		String[] cols = {"N° CDE", "Typologie", "Nb pcs", "H", "Statut"};
		modelDisponibles = new DefaultTableModel(cols, 0)
		{
			public boolean isCellEditable(int r, int c) { return false; }
		};
		tblDisponibles = IhmUtils.creerTable(modelDisponibles);
		tblDisponibles.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		tblDisponibles.getSelectionModel().addListSelectionListener(e ->
		{
			if (!e.getValueIsAdjusting()) majInfoLot();
		});

		txtRechercheDisp = new JTextField();
		txtRechercheDisp.setToolTipText("Rechercher dans les lots…");
		txtRechercheDisp.addKeyListener(new KeyAdapter()
		{
			public void keyReleased(KeyEvent e) { rafraichirTableauDisponibles(); }
		});

		combFiltreStatut = new JComboBox<>(new String[]{
			"Tous les statuts", "VA - Validé avec le CP", "BL - Bloqué", "EP - Envoi au CP"});
		combFiltreStatut.addActionListener(e -> rafraichirTableauDisponibles());

		JPanel p = new JPanel(new BorderLayout(0, 4));
		p.setBackground(IhmUtils.FOND);
		p.setPreferredSize(new Dimension(340, 0));

		JPanel top = new JPanel(new BorderLayout(4, 0));
		top.setBackground(IhmUtils.FOND);
		top.add(IhmUtils.labelSection("Lots disponibles"), BorderLayout.WEST);
		top.add(txtRechercheDisp,  BorderLayout.CENTER);
		top.add(combFiltreStatut,  BorderLayout.SOUTH);   // filtre sous la recherche

		JPanel tblPanel = new JPanel(new BorderLayout());
		tblPanel.setBackground(Color.WHITE);
		tblPanel.setBorder(BorderFactory.createLineBorder(IhmUtils.BORD));
		tblPanel.add(new JScrollPane(tblDisponibles));

		p.add(top,      BorderLayout.NORTH);
		p.add(tblPanel, BorderLayout.CENTER);
		return p;
	}

	// ── Panneau central ───────────────────────────────────────────────────

	private JPanel creerPanelCentral()
	{
		JPanel p = IhmUtils.panelFormulaire(280);
		p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));

		infoLot = new JTextArea(8, 20);
		infoLot.setEditable(false);
		infoLot.setFont(new Font("Monospaced", Font.PLAIN, 12));
		infoLot.setBackground(IhmUtils.INFO);
		infoLot.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
		JScrollPane scrollInfo = new JScrollPane(infoLot);
		scrollInfo.setMaximumSize(new Dimension(Integer.MAX_VALUE, 190));
		scrollInfo.setAlignmentX(Component.LEFT_ALIGNMENT);

		combSociete = new JComboBox<>();
		combSociete.setAlignmentX(Component.LEFT_ALIGNMENT);
		combSociete.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
		combSociete.addActionListener(e -> remplirComboAce());

		combAce = new JComboBox<>();
		combAce.setAlignmentX(Component.LEFT_ALIGNMENT);
		combAce.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));

		JButton btnAffecter = IhmUtils.bouton("▶ Affecter →",          IhmUtils.HEADER,        Color.WHITE);
		JButton btnRetirer  = IhmUtils.bouton("◀ Retirer",             new Color(200, 50, 50), Color.WHITE);
		JButton btnEditer   = IhmUtils.bouton("✏ Modifier ce lot",      IhmUtils.BLEU,          Color.WHITE);
		JButton btnAjouter  = IhmUtils.bouton("+ Nouveau lot",          new Color(60, 140, 60), Color.WHITE);
		JButton btnNewLots  = IhmUtils.bouton("importer nouveau lots",  new Color(100, 20, 70), Color.WHITE);

		btnAffecter.addActionListener(e -> affecterLot());
		btnRetirer .addActionListener(e -> retirerAffectation());
		btnEditer  .addActionListener(e -> ouvrirEditionLot());
		btnAjouter .addActionListener(e -> new DialogAjoutLot(fenetre, ctrl, this).setVisible(true));
		btnNewLots .addActionListener(e -> ctrl.exportNewLot());

		lblStatut = new JLabel(" ");
		lblStatut.setFont(new Font("SansSerif", Font.ITALIC, 11));
		lblStatut.setAlignmentX(Component.LEFT_ALIGNMENT);

		IhmUtils.ajLabel(p, "Lot sélectionné");
		p.add(scrollInfo);
		p.add(Box.createVerticalStrut(10));
		IhmUtils.ajLabel(p, "Affecter à la société");
		p.add(combSociete);
		p.add(Box.createVerticalStrut(6));
		IhmUtils.ajLabel(p, "Responsable ACE");
		p.add(combAce);
		p.add(Box.createVerticalStrut(14));
		p.add(btnAffecter);
		p.add(Box.createVerticalStrut(5));
		p.add(btnRetirer);
		p.add(Box.createVerticalStrut(14));
		p.add(IhmUtils.separateur());
		p.add(Box.createVerticalStrut(10));
		p.add(btnEditer);
		p.add(Box.createVerticalStrut(5));
		p.add(btnAjouter);
		p.add(Box.createVerticalStrut(5));
		p.add(btnNewLots);
		p.add(Box.createVerticalStrut(10));
		p.add(lblStatut);

		return p;
	}

	// ── Tableau droit ─────────────────────────────────────────────────────

	private JPanel creerTableauAffectes()
	{
		String[] cols = {"N° CDE", "Typologie", "Société", "ACE", "H Piste"};
		modelAffectes = new DefaultTableModel(cols, 0)
		{
			public boolean isCellEditable(int r, int c) { return false; }
		};
		tblAffectes = IhmUtils.creerTable(modelAffectes);

		txtRechercheAff = new JTextField();
		txtRechercheAff.setToolTipText("Rechercher dans les lots affectés…");
		txtRechercheAff.addKeyListener(new KeyAdapter()
		{
			public void keyReleased(KeyEvent e) { rafraichirTableauAffectes(); }
		});

		JPanel p = new JPanel(new BorderLayout(0, 4));
		p.setBackground(IhmUtils.FOND);
		p.setPreferredSize(new Dimension(380, 0));

		JPanel top = new JPanel(new BorderLayout(4, 0));
		top.setBackground(IhmUtils.FOND);
		top.add(IhmUtils.labelSection("Lots affectés"), BorderLayout.WEST);
		top.add(txtRechercheAff, BorderLayout.CENTER);

		JPanel tblPanel = new JPanel(new BorderLayout());
		tblPanel.setBackground(Color.WHITE);
		tblPanel.setBorder(BorderFactory.createLineBorder(IhmUtils.BORD));
		tblPanel.add(new JScrollPane(tblAffectes));

		p.add(top,      BorderLayout.NORTH);
		p.add(tblPanel, BorderLayout.CENTER);
		return p;
	}

	// ── Combos ────────────────────────────────────────────────────────────

	public void remplirComboSocietes()
	{
		combSociete.removeAllItems();
		combSociete.addItem("— Choisir une société —");
		for (Societe s : ctrl.getSocietes())
			combSociete.addItem(s.getNom() + "  (" + s.getTotalHeuresCE() + "h dispo)");
	}

	private void remplirComboAce()
	{
		combAce.removeAllItems();
		combAce.addItem("— Choisir un ACE —");
		int idx = combSociete.getSelectedIndex() - 1;
		if (idx < 0 || idx >= ctrl.getSocietes().size()) return;
		for (Ace a : ctrl.getSocietes().get(idx).getAces())
		{
			int nbPcs = a.getLots().stream().mapToInt(Lot::getNbPieces).sum();
			combAce.addItem(a.getNom() + " (" + a.getLots().size() + " Lot(s) pour " + nbPcs + " Pieces )");
		}
	}

	// ── Info lot ──────────────────────────────────────────────────────────

	private void majInfoLot()
	{
		Lot lot = getLotSelectionne();
		if (lot == null) { infoLot.setText(""); return; }

		StringBuilder sb = new StringBuilder();
		sb.append("N° CDE  : ").append(lot.getNumCDE()).append("\n");
		sb.append("Typo    : ").append(safe(lot.getTypologie())).append("\n");
		sb.append("Affaire : ").append(safe(lot.getAffaire())).append("\n");
		sb.append("Pièces  : ").append(String.format("%,d", lot.getNbPieces())).append("\n");
		sb.append("Cadence : ").append(String.format("%.2f p/h", lot.getCadence())).append("\n");
		sb.append("Heures  : ").append(IhmUtils.fmt(lot.getHeures())).append("\n");
		sb.append("VVS     : ").append(String.format("%,d", lot.getValeurVente())).append(" €\n");
		sb.append("PU      : ").append(String.format("%.2f €", lot.getPrixUnitaire())).append("\n");
		sb.append("Statut  : ").append(safe(lot.getStatutEchant())).append("\n");
		sb.append("Sem.    : ").append(safe(lot.getSemaine())).append("\n");
		infoLot.setText(sb.toString());
	}

	private String safe(String s) { return (s != null && !s.isEmpty()) ? s : "—"; }

	// ── Actions ───────────────────────────────────────────────────────────

	private void affecterLot()
	{
		Lot lot    = getLotSelectionne();
		int idxSoc = combSociete.getSelectedIndex() - 1;
		int idxAce = combAce.getSelectedIndex() - 1;

		if (lot == null) { afficherStatut("Sélectionnez un lot dans le tableau.",   IhmUtils.ROUGE); return; }
		if (idxSoc < 0)  { afficherStatut("Choisissez une société.",                IhmUtils.ROUGE); return; }
		if (idxAce < 0)  { afficherStatut("Choisissez un ACE.",                     IhmUtils.ROUGE); return; }

		Societe soc = ctrl.getSocietes().get(idxSoc);
		Ace     ace = soc.getAces().get(idxAce);

		if (ctrl.affecterLot(lot, soc, ace))
		{
			afficherStatut("Lot " + lot.getNumCDE() + " → " + soc.getNom() + " / " + ace.getNom(), IhmUtils.VERT);
			remplirComboSocietes();
			fenetre.rafraichirTout();
		}
		else
		{
			afficherStatut("Heures insuffisantes ! Besoin: " + IhmUtils.fmt(lot.getHeures())
				+ "  Dispo: " + soc.getTotalHeuresCE() + "h", IhmUtils.ROUGE);
		}
	}

	private void retirerAffectation()
	{
		Lot lot = getLotAffecteSelectionne();
		if (lot == null) lot = getLotSelectionne();
		if (lot == null)                       { afficherStatut("Sélectionnez un lot à désaffecter.", IhmUtils.ROUGE); return; }
		if (ctrl.getSocieteDuLot(lot) == null) { afficherStatut("Ce lot n'est pas affecté.",          IhmUtils.AMBER); return; }

		ctrl.desaffecterLot(lot);
		afficherStatut("Lot " + lot.getNumCDE() + " désaffecté.", Color.DARK_GRAY);
		remplirComboSocietes();
		fenetre.rafraichirTout();
	}

	private void ouvrirEditionLot()
	{
		Lot lot = getLotSelectionne();
		if (lot == null) lot = getLotAffecteSelectionne();
		if (lot == null) { afficherStatut("Sélectionnez un lot à modifier.", IhmUtils.ROUGE); return; }
		new DialogEditLot(fenetre, ctrl, lot, this).setVisible(true);
	}

	// ── Sélection depuis listes de référence (cf. livraison) ─────────────

	private Lot getLotSelectionne()
	{
		int row = tblDisponibles.getSelectedRow();
		if (row < 0 || row >= lotsDisponiblesAffiches.size()) return null;
		return lotsDisponiblesAffiches.get(row);
	}

	private Lot getLotAffecteSelectionne()
	{
		int row = tblAffectes.getSelectedRow();
		if (row < 0 || row >= lotsAffectesAffiches.size()) return null;
		return lotsAffectesAffiches.get(row);
	}

	private String safe2(String s) { return s != null ? s : ""; }

	// ── Rafraîchissement ──────────────────────────────────────────────────

	public void rafraichir()
	{
		rafraichirTableauDisponibles();
		rafraichirInfo();
		rafraichirTableauAffectes();
		majInfoLot();
	}

	private void rafraichirTableauDisponibles()
	{
		modelDisponibles.setRowCount(0);
		lotsDisponiblesAffiches.clear();

		String filtre    = combFiltreStatut != null && combFiltreStatut.getSelectedIndex() > 0
			? (String) combFiltreStatut.getSelectedItem() : "";
		String recherche = txtRechercheDisp != null ? txtRechercheDisp.getText().toLowerCase() : "";

		for (Lot l : ctrl.getLots())
		{
			if (l.isEstSousDouane()) continue;
			if (ctrl.getSocieteDuLot(l) != null) continue;

			if (!filtre.isEmpty()
				&& !filtre.equals(l.getStatutEchant())
				&& !filtre.equals(l.getStatut())) continue;

			if (!recherche.isEmpty()
				&& !String.valueOf(l.getNumCDE()).contains(recherche)
				&& !safe2(l.getTypologie()).toLowerCase().contains(recherche)
				&& !safe2(l.getAffaire()).toLowerCase().contains(recherche)) continue;

			lotsDisponiblesAffiches.add(l);

			modelDisponibles.addRow(new Object[]{
				l.getNumCDE(),
				safe2(l.getTypologie()).length() > 28
					? safe2(l.getTypologie()).substring(0, 28) + "…" : safe2(l.getTypologie()),
				l.getNbPieces(),
				IhmUtils.fmt(l.getHeures()),
				safe2(l.getStatutEchant())
			});
		}
	}

	private void rafraichirInfo()
	{
		try
		{
			if (combSociete != null) remplirComboSocietes();
			if (combAce     != null) remplirComboAce();
		}
		catch (NullPointerException ignored) {}
	}

	private void rafraichirTableauAffectes()
	{
		modelAffectes.setRowCount(0);
		lotsAffectesAffiches.clear();

		String filtre = txtRechercheAff != null ? txtRechercheAff.getText().toLowerCase() : "";

		for (Societe soc : ctrl.getSocietes())
			for (Lot l : soc.getLots())
			{
				if (!filtre.isEmpty()
					&& !String.valueOf(l.getNumCDE()).contains(filtre)
					&& !safe2(l.getTypologie()).toLowerCase().contains(filtre)
					&& !soc.getNom().toLowerCase().contains(filtre)) continue;

				Ace ace = ctrl.getAceDuLot(l);
				lotsAffectesAffiches.add(l);

				modelAffectes.addRow(new Object[]{
					l.getNumCDE(),
					safe2(l.getTypologie()).length() > 22
						? safe2(l.getTypologie()).substring(0, 22) + "…" : safe2(l.getTypologie()),
					soc.getNom(),
					ace != null ? ace.getNom() : "—",
					IhmUtils.fmt(l.getHeuresAce())
				});
			}
	}

	public void afficherStatut(String msg, Color couleur)
	{
		lblStatut.setText(msg);
		lblStatut.setForeground(couleur);
		Timer t = new Timer(4000, e -> lblStatut.setText(" "));
		t.setRepeats(false);
		t.start();
	}
}