package app.ihm.ficheroute;

import app.Controleur;
import app.ihm.FenetrePrincipale;
import app.ihm.IhmUtils;
import app.metier.lot.Lot;
import app.metier.lot.Methode;
import app.metier.personelle.Ace;
import app.metier.personelle.Societe;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.swing.*;

public class PanelFicheRoute extends JPanel
{
	private final Controleur       ctrl;
	private final FenetrePrincipale fenetre;
	private java.util.Map<Ace, Boolean> aceExpanded = new java.util.HashMap<>();

	// ── Sous-onglet "Par Société" ─────────────────────────────────────────
	private JComboBox<String> combSociete;
	private JLabel  lblVVS_s, lblPieces_s, lblPU_s, lblHeures_s, lblHeures_s2, lblNbLots_s;
	private JPanel  panelRecapAce_s;
	private JPanel  panelCartes_s;
	private Societe societeCourante;

	// ── Sous-onglet "Par ACE" ─────────────────────────────────────────────
	private JComboBox<String> combAce;
	private JLabel  lblVVS_a, lblPieces_a, lblHeures_a, lblHeures_a2;
	private JLabel  lblVVS_a2, lblPieces_a2;
	private JPanel  panelCartes_a;
	private Ace     aceCourante;
	private String  nomAceMemorise = null;

	public PanelFicheRoute(Controleur ctrl, FenetrePrincipale fenetre)
	{
		this.ctrl    = ctrl;
		this.fenetre = fenetre;
		setLayout(new BorderLayout());
		setBackground(IhmUtils.FOND);

		JTabbedPane tabs = new JTabbedPane();
		tabs.setFont(new Font("SansSerif", Font.PLAIN, 13));
		tabs.addTab("\uD83C\uDFE2 Par Société", creerPanelSociete());
		tabs.addTab("\uD83D\uDC64 Par ACE",     creerPanelAce());
		add(tabs, BorderLayout.CENTER);
	}

	// ── ONGLET PAR SOCIÉTÉ ────────────────────────────────────────────────

	private JPanel creerPanelSociete()
	{
		JPanel p = new JPanel(new BorderLayout(0, 6));
		p.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
		p.setBackground(IhmUtils.FOND);
		p.add(creerHautSociete(), BorderLayout.NORTH);

		panelCartes_s = new JPanel();
		panelCartes_s.setLayout(new BoxLayout(panelCartes_s, BoxLayout.Y_AXIS));
		panelCartes_s.setBackground(new Color(240, 242, 245));

		JScrollPane scroll = new JScrollPane(panelCartes_s,
			JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
			JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		scroll.getVerticalScrollBar().setUnitIncrement(20);
		scroll.setBorder(BorderFactory.createLineBorder(IhmUtils.BORD));
		p.add(scroll, BorderLayout.CENTER);
		return p;
	}

	private JPanel creerHautSociete()
	{
		JPanel p = new JPanel(new BorderLayout(0, 6));
		p.setBackground(IhmUtils.FOND);

		JPanel ligneSelect = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
		ligneSelect.setBackground(IhmUtils.FOND);
		JLabel lbl = new JLabel("Société : ");
		lbl.setFont(new Font("SansSerif", Font.BOLD, 13));
		combSociete = new JComboBox<>();
		combSociete.setFont(new Font("SansSerif", Font.PLAIN, 13));
		combSociete.setPreferredSize(new Dimension(280, 28));
		combSociete.addActionListener(e -> changerSociete());
		JButton btnMeth = IhmUtils.bouton("\uD83D\uDC41 Voir la Méthode", IhmUtils.VERT, Color.WHITE);
		btnMeth.addActionListener(e -> ouvrirMeth_s());
		ligneSelect.add(lbl); ligneSelect.add(combSociete); ligneSelect.add(btnMeth);

		JPanel tuiles = new JPanel(new GridLayout(1, 6, 6, 0));
		tuiles.setBackground(IhmUtils.FOND);
		tuiles.setPreferredSize(new Dimension(0, 64));
		lblNbLots_s  = creerTuile("Lots affectés", "—", IhmUtils.BLEU);
		lblVVS_s     = creerTuile("VVS. Total",    "—", IhmUtils.VERT);
		lblPieces_s  = creerTuile("Nb Pièces",     "—", new Color(0, 80, 140));
		lblPU_s      = creerTuile("PU. Moyen",     "—", IhmUtils.AMBER);
		lblHeures_s  = creerTuile("Av. étiq",      "—", IhmUtils.ROUGE);
		lblHeures_s2 = creerTuile("Av. valeur",    "—", IhmUtils.ROUGE);
		tuiles.add(lblNbLots_s); tuiles.add(lblVVS_s); tuiles.add(lblPieces_s);
		tuiles.add(lblPU_s);     tuiles.add(lblHeures_s); tuiles.add(lblHeures_s2);

		panelRecapAce_s = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
		panelRecapAce_s.setBackground(IhmUtils.FOND);
		JScrollPane scroll = new JScrollPane(panelRecapAce_s);
		scroll.getHorizontalScrollBar().setUnitIncrement(20);
		scroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_NEVER);
		scroll.getHorizontalScrollBar().setPreferredSize(new Dimension(0, 0));
		scroll.getHorizontalScrollBar().setVisible(false);

		JPanel nord = new JPanel();
		nord.setLayout(new BoxLayout(nord, BoxLayout.Y_AXIS));
		nord.setBackground(IhmUtils.FOND);
		JLabel titreTotaux = new JLabel("  TOTAUX GLOBAUX");
		titreTotaux.setFont(new Font("SansSerif", Font.BOLD, 11));
		titreTotaux.setForeground(Color.GRAY);
		nord.add(ligneSelect);
		nord.add(Box.createVerticalStrut(4));
		nord.add(titreTotaux);
		nord.add(tuiles);
		nord.add(Box.createVerticalStrut(6));
		nord.add(scroll);
		p.add(nord, BorderLayout.NORTH);
		return p;
	}

	// ── ONGLET PAR ACE ────────────────────────────────────────────────────

	private JPanel creerPanelAce()
	{
		JPanel p = new JPanel(new BorderLayout(0, 6));
		p.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
		p.setBackground(IhmUtils.FOND);
		p.add(creerHautAce(), BorderLayout.NORTH);

		panelCartes_a = new JPanel();
		panelCartes_a.setLayout(new BoxLayout(panelCartes_a, BoxLayout.Y_AXIS));
		panelCartes_a.setBackground(new Color(240, 242, 245));

		JScrollPane scroll = new JScrollPane(panelCartes_a,
			JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
			JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		scroll.getVerticalScrollBar().setUnitIncrement(20);
		scroll.setBorder(BorderFactory.createLineBorder(IhmUtils.BORD));
		p.add(scroll, BorderLayout.CENTER);
		return p;
	}

	private JPanel creerHautAce()
	{
		JPanel p = new JPanel(new BorderLayout());
		p.setBackground(IhmUtils.FOND);

		JPanel ligneSelect = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
		ligneSelect.setBackground(IhmUtils.FOND);
		JLabel lbl = new JLabel("ACE : ");
		lbl.setFont(new Font("SansSerif", Font.BOLD, 13));
		combAce = new JComboBox<>();
		combAce.setPreferredSize(new Dimension(280, 28));
		combAce.addActionListener(e -> changerAce());
		JButton btnMeth = IhmUtils.bouton("\uD83D\uDC41 Voir la Méthode", IhmUtils.VERT, Color.WHITE);
		btnMeth.addActionListener(e -> ouvrirMeth_a());
		ligneSelect.add(lbl); ligneSelect.add(combAce); ligneSelect.add(btnMeth);

		JPanel tuiles = new JPanel(new GridLayout(1, 6, 6, 0));
		tuiles.setBackground(IhmUtils.FOND);
		tuiles.setPreferredSize(new Dimension(0, 64));
		lblVVS_a2    = creerTuile("VVS. global",  "—", IhmUtils.BLEU);
		lblPieces_a2 = creerTuile("Pcs. global",  "—", new Color(0, 80, 140));
		lblVVS_a     = creerTuile("VVS. étiq",    "—", IhmUtils.VERT);
		lblPieces_a  = creerTuile("Pcs. étiq",    "—", new Color(0, 80, 140));
		lblHeures_a  = creerTuile("Av. étiq",     "—", IhmUtils.ROUGE);
		lblHeures_a2 = creerTuile("Av. valeur",   "—", IhmUtils.ROUGE);
		tuiles.add(lblVVS_a2); tuiles.add(lblVVS_a);
		tuiles.add(lblPieces_a2); tuiles.add(lblPieces_a);
		tuiles.add(lblHeures_a); tuiles.add(lblHeures_a2);

		JPanel nord = new JPanel();
		nord.setLayout(new BoxLayout(nord, BoxLayout.Y_AXIS));
		nord.setBackground(IhmUtils.FOND);
		nord.add(ligneSelect);
		nord.add(Box.createVerticalStrut(5));
		nord.add(tuiles);
		p.add(nord, BorderLayout.NORTH);
		return p;
	}

	// ── En-têtes de section ───────────────────────────────────────────────

	private JPanel creerEnteteAce(Ace ace, List<Lot> lots)
	{
		boolean ouvert = aceExpanded.getOrDefault(ace, true);
		JPanel p = new JPanel(new BorderLayout(8, 0));
		p.setBackground(ace.getColor());
		p.setBorder(BorderFactory.createEmptyBorder(7, 14, 7, 14));
		p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
		String fleche = ouvert ? "▼" : "▶";
		JLabel titre = new JLabel(fleche + "  " + ace.getNom() + "   (" + lots.size() + " lot(s))");
		titre.setFont(new Font("SansSerif", Font.BOLD, 13));
		titre.setForeground(Color.WHITE);
		int vvs = 0, pcs = 0;
		for (Lot l : lots) { vvs += l.getValeurVente(); pcs += l.getNbPieces(); }
		JLabel stats = new JLabel(String.format("VVS : %,d €   |   Pièces : %,d", vvs, pcs));
		stats.setFont(new Font("SansSerif", Font.PLAIN, 11));
		stats.setForeground(new Color(200, 220, 255));
		p.add(titre, BorderLayout.CENTER);
		p.add(stats, BorderLayout.EAST);
		p.setCursor(new Cursor(Cursor.HAND_CURSOR));
		p.addMouseListener(new java.awt.event.MouseAdapter()
		{
			public void mouseClicked(java.awt.event.MouseEvent e)
			{
				aceExpanded.put(ace, !aceExpanded.getOrDefault(ace, true));
				chargerFicheRouteSociete();
			}
		});
		return p;
	}

	private JPanel creerEnteteSection(String titre, Color c)
	{
		JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 5));
		p.setBackground(c);
		p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
		JLabel l = new JLabel(titre);
		l.setFont(new Font("SansSerif", Font.BOLD, 12));
		l.setForeground(Color.WHITE);
		p.add(l);
		return p;
	}

	// ── Chargement société ────────────────────────────────────────────────

	public void remplirComboSocietes()
	{
		int sel = combSociete.getSelectedIndex();
		combSociete.removeAllItems();
		combSociete.addItem("— Choisir une société —");
		for (Societe s : ctrl.getSocietes())
			combSociete.addItem(s.getNom() + "  (" + s.getLots().size() + " lots)");
		if (sel >= 0 && sel < combSociete.getItemCount())
			combSociete.setSelectedIndex(sel);
	}

	private void changerSociete()
	{
		int idx = combSociete.getSelectedIndex() - 1;
		if (idx < 0 || idx >= ctrl.getSocietes().size())
		{
			societeCourante = null;
			viderRecap_s();
			panelCartes_s.removeAll();
			panelCartes_s.revalidate();
			panelCartes_s.repaint();
			panelRecapAce_s.removeAll();
			panelRecapAce_s.revalidate();
			panelRecapAce_s.repaint();
			return;
		}
		societeCourante = ctrl.getSocietes().get(idx);
		chargerFicheRouteSociete();
	}

	private void chargerFicheRouteSociete()
	{
		if (societeCourante == null) return;

		int vvs = 0, vvsE = 0, pcs = 0, cntPU = 0, pcsT = 0;
		double sumPU = 0, etiq = 0;
		for (Lot lot : societeCourante.getLots())
		{
			vvsE += lot.getSuivieProd().getNbPieceEtiq() * lot.getPrixUnitaire();
			vvs  += lot.getValeurVente();
			pcs  += lot.getNbPieces();
			pcsT += lot.getNbPieces();
			if (lot.getNbPieces() > 0) { sumPU += lot.getPrixUnitaire(); cntPU++; }
			if (lot.getNbPieces() > 0 && lot.getSuivieProd() != null)
				etiq += lot.getSuivieProd().getNbPieceEtiq();
		}
		double puMoy = cntPU > 0 ? sumPU / cntPU : 0;
		double avE   = pcsT  > 0 ? 100.0 * etiq / pcsT  : 0;
		double avV   = vvs   > 0 ? 100.0 * vvsE / vvs   : 0;

		majTuile(lblNbLots_s,  "Lots affectés", String.valueOf(societeCourante.getLots().size()), IhmUtils.BLEU);
		majTuile(lblVVS_s,     "VVS. Total",    vvs  > 0 ? String.format("%,d €",  vvs)  : "—", IhmUtils.VERT);
		majTuile(lblPieces_s,  "Nb Pièces",     String.format("%,d", pcs), new Color(0, 80, 140));
		majTuile(lblPU_s,      "PU. Moyen",     puMoy > 0 ? String.format("%.2f €", puMoy) : "—", IhmUtils.AMBER);
		majTuile(lblHeures_s,  "Av. étiq",      avE  > 0 ? String.format("%.1f %%", avE)  : "—", IhmUtils.ROUGE);
		majTuile(lblHeures_s2, "Av. valeur",    avV  > 0 ? String.format("%.1f %%", avV)  : "—", IhmUtils.ROUGE);

		reconstruireRecapAce_s();

		panelCartes_s.removeAll();
		List<Ace> aces = societeCourante.getAces();
		if (aces == null || aces.isEmpty())
		{
			for (Lot lot : societeCourante.getLots())
				panelCartes_s.add(new CarteLot(lot, IhmUtils.BLEU, this.ctrl, this));
		}
		else
		{
			java.util.Set<Lot> dansAce = new java.util.HashSet<>();
			for (Ace ace : aces) if (ace.getLots() != null) dansAce.addAll(ace.getLots());

			for (Ace ace : aces)
			{
				List<Lot> lotsAce = ace.getLots() != null ? ace.getLots() : new ArrayList<>();
				panelCartes_s.add(creerEnteteAce(ace, lotsAce));
				if (aceExpanded.getOrDefault(ace, true))
					for (Lot lot : lotsAce)
						panelCartes_s.add(new CarteLot(lot, ace, this.ctrl, this));
			}

			List<Lot> sans = new ArrayList<>();
			for (Lot lot : societeCourante.getLots())
				if (!dansAce.contains(lot)) sans.add(lot);
			if (!sans.isEmpty())
			{
				panelCartes_s.add(creerEnteteSection(
					"Sans ACE (" + sans.size() + " lot(s))", new Color(80, 80, 80)));
				for (Lot lot : sans)
					panelCartes_s.add(new CarteLot(lot, new Color(80, 80, 80), this.ctrl, this));
			}
		}
		panelCartes_s.add(Box.createVerticalGlue());
		panelCartes_s.revalidate();
		panelCartes_s.repaint();
	}

	// ── Chargement ACE ────────────────────────────────────────────────────

	public void remplirComboAces()
	{
		if (aceCourante != null) nomAceMemorise = aceCourante.getNom();
		combAce.removeAllItems();
		combAce.addItem("— Choisir une ACE —");
		int restore = 0;
		List<Ace> aces = ctrl.getTouteAces();
		for (int i = 0; i < aces.size(); i++)
		{
			Ace a = aces.get(i);
			combAce.addItem(a.getNom() + " (" + a.getLots().size() + " lots)");
			if (nomAceMemorise != null && nomAceMemorise.equals(a.getNom())) restore = i + 1;
		}
		if (restore > 0) combAce.setSelectedIndex(restore);
	}

	private void changerAce()
	{
		int idx = combAce.getSelectedIndex() - 1;
		if (idx < 0 || idx >= ctrl.getTouteAces().size())
		{
			aceCourante = null; nomAceMemorise = null;
			panelCartes_a.removeAll();
			panelCartes_a.revalidate();
			panelCartes_a.repaint();
			viderRecap_a();
			return;
		}
		aceCourante    = ctrl.getTouteAces().get(idx);
		nomAceMemorise = aceCourante.getNom();
		chargerFicheRouteAce();
	}

	private void chargerFicheRouteAce()
	{
		if (aceCourante == null) return;
		List<Lot> lots = aceCourante.getLots();
		int vvsE = 0, vvsT = 0, pcsE = 0, pcsT = 0;
		double etiq = 0;
		for (Lot l : lots)
		{
			vvsE += l.getSuivieProd().getNbPieceEtiq() * l.getPrixUnitaire();
			vvsT += l.getValeurVente();
			pcsE += l.getSuivieProd().getNbPieceEtiq();
			pcsT += l.getNbPieces();
			if (l.getNbPieces() > 0 && l.getSuivieProd() != null)
				etiq += l.getSuivieProd().getNbPieceEtiq();
		}
		double avE = pcsT > 0 ? 100.0 * etiq / pcsT : 0;
		double avV = vvsT > 0 ? 100.0 * vvsE / vvsT : 0;

		majTuile(lblVVS_a2,    "VVS. global", vvsT > 0 ? String.format("%,d €", vvsT) : "—", IhmUtils.BLEU);
		majTuile(lblPieces_a2, "Pcs. global", String.format("%,d", pcsT),                      new Color(0, 80, 140));
		majTuile(lblVVS_a,     "VVS. étiq",   vvsE > 0 ? String.format("%,d €", vvsE) : "—", IhmUtils.VERT);
		majTuile(lblPieces_a,  "Pcs. étiq",   String.format("%,d", pcsE),                      new Color(0, 80, 140));
		majTuile(lblHeures_a,  "Av. étiq",    avE  > 0 ? String.format("%.1f %%", avE) : "—", IhmUtils.ROUGE);
		majTuile(lblHeures_a2, "Av. valeur",  avV  > 0 ? String.format("%.1f %%", avV) : "—", IhmUtils.ROUGE);

		panelCartes_a.removeAll();
		panelCartes_a.add(creerEnteteSection(
			"▶  " + aceCourante.getNom() + "  (" + lots.size() + " lot(s))", aceCourante.getColor()));
		for (Lot lot : lots)
			panelCartes_a.add(new CarteLot(lot, ctrl.getAceDuLot(lot).getColor(), this.ctrl, this));
		panelCartes_a.add(Box.createVerticalGlue());
		panelCartes_a.revalidate();
		panelCartes_a.repaint();
	}

	// ── Récap ACE ─────────────────────────────────────────────────────────

	private void reconstruireRecapAce_s()
	{
		panelRecapAce_s.removeAll();
		if (societeCourante == null || societeCourante.getAces() == null
				|| societeCourante.getAces().isEmpty())
		{
			panelRecapAce_s.revalidate(); panelRecapAce_s.repaint(); return;
		}
		for (Ace ace : societeCourante.getAces())
		{
			List<Lot> lots = ace.getLots() != null ? ace.getLots() : new ArrayList<>();
			int vvsE = 0, vvsV = 0, pcsE = 0, pcsT = 0, etiq = 0;
			for (Lot l : lots)
			{
				vvsE += l.getSuivieProd().getNbPieceEtiq() * l.getPrixUnitaire();
				vvsV += l.getValeurVente();
				pcsE += l.getSuivieProd().getNbPieceEtiq();
				pcsT += l.getNbPieces();
				if (l.getNbPieces() > 0 && l.getSuivieProd() != null)
					etiq += l.getSuivieProd().getNbPieceEtiq();
			}
			double puMoy = pcsE != 0 ? (double) vvsE / pcsE : 0;
			double avE   = pcsT > 0  ? 100.0 * etiq / pcsT  : 0;
			double avV   = vvsV > 0  ? 100.0 * vvsE / vvsV  : 0;
			Color  col   = ace.getColor();

			JPanel grp = new JPanel(new BorderLayout(0, 2));
			grp.setBackground(IhmUtils.FOND);
			grp.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(col, 2),
				BorderFactory.createEmptyBorder(2, 4, 2, 4)));

			JLabel titLbl = new JLabel(" " + ace.getNom(), JLabel.LEFT);
			titLbl.setFont(new Font("SansSerif", Font.BOLD, 11));
			titLbl.setForeground(Color.WHITE);
			titLbl.setOpaque(true);
			titLbl.setBackground(col);
			titLbl.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));

			JPanel t3 = new JPanel(new GridLayout(1, 5, 4, 0));
			t3.setBackground(IhmUtils.FOND);
			t3.add(creerTuileMini("VVS. étiq",    vvsE > 0 ? String.format("%,d €",   vvsE) : "—", col));
			t3.add(creerTuileMini("Pièces étiq",  String.format("%,d", pcsE),                        col));
			t3.add(creerTuileMini("PU.Moy étiq.", puMoy > 0 ? String.format("%.2f €", puMoy) : "—", col));
			t3.add(creerTuileMini("Av. étiq",     avE  > 0 ? String.format("%.1f %%", avE)  : "—", col));
			t3.add(creerTuileMini("Av. valeur",   avV  > 0 ? String.format("%.1f %%", avV)  : "—", col));

			grp.add(titLbl, BorderLayout.NORTH);
			grp.add(t3,     BorderLayout.CENTER);
			panelRecapAce_s.add(grp);
		}
		panelRecapAce_s.revalidate();
		panelRecapAce_s.repaint();
	}

	// ── Boutons méthode ───────────────────────────────────────────────────

	private void ouvrirMeth_s()
	{
		if (societeCourante == null) return;
		ouvrirMethode(societeCourante.getLots().stream()
			.map(Lot::getMethode).filter(Objects::nonNull)
			.distinct().collect(Collectors.toList()));
	}

	private void ouvrirMeth_a()
	{
		if (aceCourante == null) return;
		ouvrirMethode(aceCourante.getLots().stream()
			.map(Lot::getMethode).filter(Objects::nonNull)
			.distinct().collect(Collectors.toList()));
	}

	private void ouvrirMethode(List<Methode> methodes)
	{
		if (methodes.isEmpty())
		{
			JOptionPane.showMessageDialog(this, "Aucune méthode.", "Info", JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		String[] opts = methodes.stream().map(Methode::getNom).toArray(String[]::new);
		String choix = (String) JOptionPane.showInputDialog(this, "Choisir la méthode :",
			"Voir Méthode", JOptionPane.PLAIN_MESSAGE, null, opts, opts[0]);
		if (choix == null) return;
		methodes.stream().filter(m -> m.getNom().equals(choix)).findFirst().ifPresent(Methode::ouvrir);
	}

	// ── Tuiles ────────────────────────────────────────────────────────────

	private JLabel creerTuile(String titre, String val, Color c)
	{
		JLabel l = new JLabel(buildTuileHtml(titre, val, c));
		l.setOpaque(true);
		l.setBackground(Color.WHITE);
		l.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(IhmUtils.BORD),
			BorderFactory.createEmptyBorder(4, 8, 4, 8)));
		return l;
	}

	private JLabel creerTuileMini(String titre, String val, Color c)
	{
		String hex = String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
		JLabel l = new JLabel("<html><span style='font-size:8px;color:#888;'>" + titre
			+ "</span><br><b style='font-size:12px;color:" + hex + ";'>" + val + "</b></html>");
		l.setOpaque(true);
		l.setBackground(Color.WHITE);
		l.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(IhmUtils.BORD),
			BorderFactory.createEmptyBorder(2, 6, 2, 6)));
		l.setPreferredSize(new Dimension(88, 44));
		return l;
	}

	private String buildTuileHtml(String titre, String val, Color c)
	{
		String hex = String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
		return "<html><span style='font-size:9px;color:#888;'>" + titre
			+ "</span><br><b style='font-size:14px;color:" + hex + ";'>" + val + "</b></html>";
	}

	private void majTuile(JLabel t, String titre, String val, Color c)
	{ t.setText(buildTuileHtml(titre, val, c)); }

	private void viderRecap_s()
	{
		majTuile(lblNbLots_s,  "Lots affectés", "—", IhmUtils.BLEU);
		majTuile(lblVVS_s,     "VVS. Total",    "—", IhmUtils.VERT);
		majTuile(lblPieces_s,  "Nb Pièces",     "—", new Color(0, 80, 140));
		majTuile(lblPU_s,      "PU. Moyen",     "—", IhmUtils.AMBER);
		majTuile(lblHeures_s,  "Av. étiq",      "—", IhmUtils.ROUGE);
		majTuile(lblHeures_s2, "Av. valeur",    "—", IhmUtils.ROUGE);
	}

	private void viderRecap_a()
	{
		majTuile(lblVVS_a2,    "VVS. global", "—", IhmUtils.VERT);
		majTuile(lblPieces_a2, "Pcs. global", "—", new Color(0, 80, 140));
		majTuile(lblVVS_a,     "VVS. étiq",   "—", IhmUtils.VERT);
		majTuile(lblPieces_a,  "Pcs. étiq",   "—", new Color(0, 80, 140));
		majTuile(lblHeures_a,  "Av. étiq",    "—", IhmUtils.ROUGE);
		majTuile(lblHeures_a2, "Av. valeur",  "—", IhmUtils.ROUGE);
	}

	// ── Rafraîchissement ──────────────────────────────────────────────────

	public void rafraichir()
	{
		Societe sv = societeCourante;
		Ace     av = aceCourante;
		remplirComboSocietes();
		remplirComboAces();
		if (sv != null)
		{
			societeCourante = sv;
			for (int i = 0; i < combSociete.getItemCount(); i++)
			{
				String item = combSociete.getItemAt(i);
				if (item != null && item.startsWith(sv.getNom()))
				{ combSociete.setSelectedIndex(i); break; }
			}
		}
		chargerFicheRouteSociete();
		if (av != null)
		{
			aceCourante = av;
			for (int i = 0; i < combAce.getItemCount(); i++)
			{
				String item = combAce.getItemAt(i);
				if (item != null && item.startsWith(av.getNom()))
				{ combAce.setSelectedIndex(i); break; }
			}
			chargerFicheRouteAce();
		}
		ctrl.autoSauvegarde();
	}
}