package app.ihm.serveur;

import app.ServeurHTTP;
import app.ihm.IhmUtils;
import app.metier.lot.Lot;
import app.metier.personelle.Ace;
import app.metier.personelle.Societe;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.*;

/**
 * Panneau d'affectation côté serveur — conçu pour 860×680.
 *
 * Layout : JSplitPane horizontal
 *   Gauche (55%) : tableau lots + barre recherche/filtre
 *   Droite (45%) : info lot + combo société + combo ACE + boutons + tableau récap
 *
 * Tout tient dans la fenêtre sans scroll horizontal.
 */
public class PanelAffectationServeur extends JPanel
{
	private final ServeurHTTP serveur;

	// ── Données ───────────────────────────────────────────────────────────
	private ArrayList<Lot>     lotsPrep     = null;
	private ArrayList<Societe> socsPrepCopy = null;
	private Lot                lotSel       = null;
	private List<Lot>          lotsAffiches = new ArrayList<>();

	// ── Tableau lots (gauche) ─────────────────────────────────────────────
	private DefaultTableModel modelLots;
	private JTable            tblLots;
	private JTextField        txtRecherche;
	private JComboBox<String> combFiltre;

	// ── Panneau action (droite) ───────────────────────────────────────────
	private JLabel            lblInfoLot;
	private JComboBox<String> combSoc;
	private JComboBox<String> combAce;
	private JLabel            lblStatut;

	// ── Tableau récap affectés (bas droite) ───────────────────────────────
	private DefaultTableModel modelRecap;
	private JTable            tblRecap;

	// ── Bandeau état ──────────────────────────────────────────────────────
	private JLabel            lblEtat;

	// ═════════════════════════════════════════════════════════════════════
	//  CONSTRUCTION
	// ═════════════════════════════════════════════════════════════════════

	public PanelAffectationServeur(ServeurHTTP serveur)
	{
		this.serveur = serveur;
		setLayout(new BorderLayout(0, 4));
		setBorder(new EmptyBorder(8, 8, 8, 8));
		setBackground(IhmUtils.FOND);

		add(construireNord(),    BorderLayout.NORTH);
		add(construireCorps(),   BorderLayout.CENTER);
	}

	// ── Bandeau nord ─────────────────────────────────────────────────────

	private JPanel construireNord()
	{
		JPanel p = new JPanel(new BorderLayout(10, 0));
		p.setBackground(IhmUtils.FOND);
		p.setBorder(new EmptyBorder(0, 0, 4, 0));

		lblEtat = new JLabel("Chargement…");
		lblEtat.setFont(new Font("SansSerif", Font.PLAIN, 12));

		p.add(lblEtat,      BorderLayout.CENTER);
		return p;
	}

	// ── Corps : split gauche/droite ───────────────────────────────────────

	private JSplitPane construireCorps()
	{
		JSplitPane split = new JSplitPane(
			JSplitPane.HORIZONTAL_SPLIT,
			construireGauche(),
			construireDroite()
		);
		split.setDividerLocation(460);   // ~55% de 860
		split.setDividerSize(4);
		split.setResizeWeight(0.55);
		split.setBorder(null);
		return split;
	}

	// ═════════════════════════════════════════════════════════════════════
	//  GAUCHE — tableau tous les lots (disponibles + affectés, couleur différente)
	// ═════════════════════════════════════════════════════════════════════

	private JPanel construireGauche()
	{
		// Colonnes : N°, Affaire, Pcs, H, Société/ACE
		String[] cols = {"N° CDE", "Affaire / Typo", "Pcs", "H", "Affectation"};
		modelLots = new DefaultTableModel(cols, 0)
		{
			public boolean isCellEditable(int r, int c) { return false; }
		};
		tblLots = IhmUtils.creerTable(modelLots);
		tblLots.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		tblLots.setRowHeight(22);

		// Renderer : vert si affecté, blanc si non affecté
		tblLots.setDefaultRenderer(Object.class, new DefaultTableCellRenderer()
		{
			public Component getTableCellRendererComponent(JTable t, Object v,
					boolean sel, boolean foc, int r, int c)
			{
				super.getTableCellRendererComponent(t, v, sel, foc, r, c);
				if (sel)
				{
					setBackground(IhmUtils.SEL);
					setForeground(IhmUtils.TEXTE);
				}
				else
				{
					// Lots affectés (colonne affectation non vide) → fond vert pâle
					Object aff = modelLots.getValueAt(r, 4);
					boolean estAff = aff != null && !aff.toString().isEmpty();
					setBackground(estAff
						? new Color(220, 248, 220)
						: (r % 2 == 0 ? Color.WHITE : new Color(249, 251, 254)));
					setForeground(IhmUtils.TEXTE);
				}
				return this;
			}
		});

		tblLots.getSelectionModel().addListSelectionListener(e ->
		{
			if (!e.getValueIsAdjusting()) selectionnerLot();
		});

		// Largeurs colonnes
		tblLots.getColumnModel().getColumn(0).setPreferredWidth(55);
		tblLots.getColumnModel().getColumn(1).setPreferredWidth(160);
		tblLots.getColumnModel().getColumn(2).setPreferredWidth(50);
		tblLots.getColumnModel().getColumn(3).setPreferredWidth(40);
		tblLots.getColumnModel().getColumn(4).setPreferredWidth(120);

		// Barre recherche + filtre
		txtRecherche = new JTextField();
		txtRecherche.setToolTipText("Rechercher…");
		txtRecherche.addKeyListener(new KeyAdapter()
		{
			public void keyReleased(KeyEvent e) { rafraichirTableauLots(); }
		});

		combFiltre = new JComboBox<>(new String[]{
			"Tous", "Non affectés", "Affectés",
			"VA", "BL", "EP"});
		combFiltre.setPreferredSize(new Dimension(110, 24));
		combFiltre.addActionListener(e -> rafraichirTableauLots());

		JPanel barreTop = new JPanel(new BorderLayout(4, 0));
		barreTop.setBackground(IhmUtils.FOND);
		barreTop.add(IhmUtils.labelSection("Lots"), BorderLayout.WEST);
		barreTop.add(txtRecherche, BorderLayout.CENTER);
		barreTop.add(combFiltre,   BorderLayout.EAST);

		JPanel tblPanel = new JPanel(new BorderLayout());
		tblPanel.setBackground(Color.WHITE);
		tblPanel.setBorder(BorderFactory.createLineBorder(IhmUtils.BORD));
		tblPanel.add(new JScrollPane(tblLots));

		JPanel p = new JPanel(new BorderLayout(0, 4));
		p.setBackground(IhmUtils.FOND);
		p.add(barreTop, BorderLayout.NORTH);
		p.add(tblPanel, BorderLayout.CENTER);
		return p;
	}

	// ═════════════════════════════════════════════════════════════════════
	//  DROITE — info + action + récap compact
	// ═════════════════════════════════════════════════════════════════════

	private JPanel construireDroite()
	{
		JPanel p = new JPanel(new BorderLayout(0, 6));
		p.setBackground(IhmUtils.FOND);
		p.setBorder(new EmptyBorder(0, 6, 0, 0));

		p.add(construireActionPanel(), BorderLayout.NORTH);
		p.add(construireRecap(),       BorderLayout.CENTER);
		return p;
	}

	// ── Panneau action (haut droite) ──────────────────────────────────────

	private JPanel construireActionPanel()
	{
		JPanel p = new JPanel();
		p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
		p.setBackground(Color.WHITE);
		p.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(IhmUtils.BORD),
			new EmptyBorder(8, 10, 8, 10)));

		// Info lot sélectionné — une seule ligne compacte
		lblInfoLot = new JLabel("← Sélectionner un lot");
		lblInfoLot.setFont(new Font("SansSerif", Font.ITALIC, 12));
		lblInfoLot.setForeground(Color.GRAY);
		lblInfoLot.setAlignmentX(Component.LEFT_ALIGNMENT);

		// Ligne société + ACE sur la même ligne
		JPanel ligneCombo = new JPanel(new GridLayout(1, 2, 6, 0));
		ligneCombo.setOpaque(false);
		ligneCombo.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
		ligneCombo.setAlignmentX(Component.LEFT_ALIGNMENT);

		combSoc = new JComboBox<>();
		combSoc.setFont(new Font("SansSerif", Font.PLAIN, 12));
		combSoc.addActionListener(e -> remplirCombAce());

		combAce = new JComboBox<>();
		combAce.setFont(new Font("SansSerif", Font.PLAIN, 12));

		ligneCombo.add(combSoc);
		ligneCombo.add(combAce);

		// Boutons sur une ligne
		JPanel ligneBtns = new JPanel(new GridLayout(1, 2, 6, 0));
		ligneBtns.setOpaque(false);
		ligneBtns.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
		ligneBtns.setAlignmentX(Component.LEFT_ALIGNMENT);

		JButton btnAff = IhmUtils.bouton("➜ Affecter", IhmUtils.VERT, Color.WHITE);
		JButton btnRet = IhmUtils.bouton("✕ Retirer",  IhmUtils.ROUGE, Color.WHITE);
		btnAff.addActionListener(e -> affecterLot());
		btnRet.addActionListener(e -> retirerLot());
		ligneBtns.add(btnAff);
		ligneBtns.add(btnRet);

		lblStatut = new JLabel(" ");
		lblStatut.setFont(new Font("SansSerif", Font.ITALIC, 11));
		lblStatut.setAlignmentX(Component.LEFT_ALIGNMENT);

		p.add(lblInfoLot);
		p.add(Box.createVerticalStrut(6));
		p.add(ligneCombo);
		p.add(Box.createVerticalStrut(5));
		p.add(ligneBtns);
		p.add(Box.createVerticalStrut(4));
		p.add(lblStatut);

		return p;
	}

	// ── Récap lots affectés (bas droite) ─────────────────────────────────

	private JPanel construireRecap()
	{
		String[] cols = {"N° CDE", "Société", "ACE"};
		modelRecap = new DefaultTableModel(cols, 0)
		{
			public boolean isCellEditable(int r, int c) { return false; }
		};
		tblRecap = IhmUtils.creerTable(modelRecap);
		tblRecap.setRowHeight(20);
		tblRecap.setFont(new Font("SansSerif", Font.PLAIN, 11));
		tblRecap.setDefaultRenderer(Object.class, new DefaultTableCellRenderer()
		{
			public Component getTableCellRendererComponent(JTable t, Object v,
					boolean sel, boolean foc, int r, int c)
			{
				super.getTableCellRendererComponent(t, v, sel, foc, r, c);
				setBackground(sel ? IhmUtils.SEL : (r % 2 == 0 ? Color.WHITE : new Color(240, 250, 240)));
				setForeground(IhmUtils.TEXTE);
				return this;
			}
		});

		// Clic sur récap → sélectionne dans le tableau gauche
		tblRecap.getSelectionModel().addListSelectionListener(e -> {
			if (!e.getValueIsAdjusting()) {
				int row = tblRecap.getSelectedRow();
				if (row < 0) return;
				int numCDE = (Integer) modelRecap.getValueAt(row, 0);
				// Retrouver et sélectionner dans le tableau gauche
				for (int i = 0; i < lotsAffiches.size(); i++)
					if (lotsAffiches.get(i).getNumCDE() == numCDE)
					{ tblLots.setRowSelectionInterval(i, i); tblLots.scrollRectToVisible(tblLots.getCellRect(i, 0, true)); break; }
			}
		});

		JPanel top = new JPanel(new BorderLayout());
		top.setBackground(IhmUtils.FOND);
		top.add(IhmUtils.labelSection("Lots pré-affectés"), BorderLayout.WEST);

		JPanel tblPanel = new JPanel(new BorderLayout());
		tblPanel.setBorder(BorderFactory.createLineBorder(IhmUtils.BORD));
		tblPanel.add(new JScrollPane(tblRecap));

		JPanel p = new JPanel(new BorderLayout(0, 4));
		p.setBackground(IhmUtils.FOND);
		p.add(top,      BorderLayout.NORTH);
		p.add(tblPanel, BorderLayout.CENTER);
		return p;
	}

	// ═════════════════════════════════════════════════════════════════════
	//  LOGIQUE AFFECTATION
	// ═════════════════════════════════════════════════════════════════════

	private void selectionnerLot()
	{
		int row = tblLots.getSelectedRow();
		if (row < 0 || row >= lotsAffiches.size()) { lotSel = null; majInfoLot(); return; }
		lotSel = lotsAffiches.get(row);
		majInfoLot();
	}

	private void majInfoLot()
	{
		if (lblInfoLot == null) return;
		if (lotSel == null)
		{
			lblInfoLot.setText("← Sélectionner un lot");
			lblInfoLot.setForeground(Color.GRAY);
			lblInfoLot.setFont(new Font("SansSerif", Font.ITALIC, 12));
			return;
		}
		Societe soc = getSocDuLot(lotSel);
		Ace     ace = getAceDuLot(lotSel);
		String affStr = soc != null
			? "  →  " + soc.getNom() + (ace != null ? " / " + ace.getNom() : "")
			: "  →  Non affecté";
		lblInfoLot.setText("N° " + lotSel.getNumCDE()
			+ "   " + trunc(safe(lotSel.getAffaire()), 22)
			+ "   " + lotSel.getNbPieces() + " pcs"
			+ "   " + String.format("%.1fh", lotSel.getHeures())
			+ affStr);
		lblInfoLot.setForeground(soc != null ? new Color(20, 120, 20) : IhmUtils.TEXTE);
		lblInfoLot.setFont(new Font("SansSerif", Font.BOLD, 12));
	}

	private void affecterLot()
	{
		if (lotSel == null)
		{ afficherStatut("Sélectionnez d'abord un lot.", IhmUtils.ROUGE); return; }

		int idxSoc = combSoc.getSelectedIndex() - 1;
		int idxAce = combAce.getSelectedIndex() - 1;

		if (idxSoc < 0) { afficherStatut("Choisissez une société.", IhmUtils.ROUGE); return; }
		if (idxAce < 0) { afficherStatut("Choisissez un ACE.",      IhmUtils.ROUGE); return; }

		Societe soc = socsPrepCopy.get(idxSoc);
		Ace     ace = soc.getAces().get(idxAce);

		retirerDeToutes(lotSel);
		soc.getLots().add(lotSel);
		ace.getLots().add(lotSel);

		serveur.sauvegarderSemaneSuivante(lotsPrep, socsPrepCopy);
		afficherStatut("Lot " + lotSel.getNumCDE() + " → " + soc.getNom() + " / " + ace.getNom(), IhmUtils.VERT);
		majInfoLot();
		rafraichirTableauLots();
		rafraichirRecap();
		majEtat();
	}

	private void retirerLot()
	{
		if (lotSel == null)
		{ afficherStatut("Sélectionnez d'abord un lot.", IhmUtils.ROUGE); return; }
		if (getSocDuLot(lotSel) == null)
		{ afficherStatut("Ce lot n'est pas affecté.", IhmUtils.AMBER); return; }

		retirerDeToutes(lotSel);
		serveur.sauvegarderSemaneSuivante(lotsPrep, socsPrepCopy);
		afficherStatut("Lot " + lotSel.getNumCDE() + " désaffecté.", Color.DARK_GRAY);
		majInfoLot();
		rafraichirTableauLots();
		rafraichirRecap();
		majEtat();
	}

	private void retirerDeToutes(Lot lot)
	{
		if (lot == null || socsPrepCopy == null) return;
		for (Societe s : socsPrepCopy)
		{
			s.getLots().removeIf(l -> l != null && lot.getId() != null && lot.getId().equals(l.getId()));
			for (Ace a : s.getAces())
				a.getLots().removeIf(l -> l != null && lot.getId() != null && lot.getId().equals(l.getId()));
		}
	}

	private void afficherStatut(String msg, Color couleur)
	{
		if (lblStatut == null) return;
		lblStatut.setText(msg);
		lblStatut.setForeground(couleur);
		Timer t = new Timer(3500, e -> lblStatut.setText(" "));
		t.setRepeats(false); t.start();
	}

	// ═════════════════════════════════════════════════════════════════════
	//  RAFRAÎCHISSEMENT
	// ═════════════════════════════════════════════════════════════════════

	/** Appelé par FenetreServeur à chaque activation de l'onglet. */
	public void chargerDonnees()
	{
		ArrayList<Lot>     ls = serveur.getLotsSemaneSuivante();
		ArrayList<Societe> ss = serveur.getSocietesSemaneSuivante();

		if (ls != null && !ls.isEmpty())
		{
			lotsPrep     = ls;
			socsPrepCopy = (ss != null && !ss.isEmpty()) ? ss : copierSocietesVides();
		}
		else
		{
			lotsPrep     = null;
			socsPrepCopy = null;
		}

		lotSel = null;
		majEtat();
		remplirCombSoc();
		rafraichirTableauLots();
		rafraichirRecap();
		majInfoLot();
	}

	private void rafraichirTableauLots()
	{
		if (modelLots == null || lotsPrep == null) { if (modelLots != null) modelLots.setRowCount(0); return; }

		// IDs affectés
		Set<String> idsAff = new HashSet<>();
		if (socsPrepCopy != null)
			for (Societe s : socsPrepCopy)
				for (Lot l : s.getLots())
					if (l != null && l.getId() != null) idsAff.add(l.getId());

		String recherche = txtRecherche != null ? txtRecherche.getText().toLowerCase() : "";
		String filtre    = combFiltre   != null ? (String) combFiltre.getSelectedItem() : "Tous";

		modelLots.setRowCount(0);
		lotsAffiches.clear();

		for (Lot l : lotsPrep)
		{
			boolean estAff = l.getId() != null && idsAff.contains(l.getId());

			// Filtre
			if ("Non affectés".equals(filtre) && estAff)  continue;
			if ("Affectés".equals(filtre)     && !estAff) continue;
			if ("VA".equals(filtre) && !safe(l.getStatutEchant()).startsWith("VA")) continue;
			if ("BL".equals(filtre) && !safe(l.getStatutEchant()).startsWith("BL")) continue;
			if ("EP".equals(filtre) && !safe(l.getStatutEchant()).startsWith("EP")) continue;

			// Recherche
			if (!recherche.isEmpty()
				&& !String.valueOf(l.getNumCDE()).contains(recherche)
				&& !safe(l.getTypologie()).toLowerCase().contains(recherche)
				&& !safe(l.getAffaire()).toLowerCase().contains(recherche)) continue;

			// Colonne affectation
			String affStr = "";
			if (estAff && socsPrepCopy != null)
			{
				for (Societe s : socsPrepCopy)
					for (Lot sl : s.getLots())
						if (sl != null && l.getId() != null && l.getId().equals(sl.getId()))
						{
							Ace ace = getAceDuLotDansSoc(l, s);
							affStr = s.getNom() + (ace != null ? "/" + ace.getNom() : "");
							break;
						}
			}

			lotsAffiches.add(l);
			String affaire = safe(l.getAffaire()).isEmpty() ? safe(l.getTypologie()) : safe(l.getAffaire());
			modelLots.addRow(new Object[]{
				l.getNumCDE(),
				trunc(affaire, 22),
				l.getNbPieces(),
				String.format("%.0f", l.getHeures()),
				affStr
			});
		}
	}

	private void rafraichirRecap()
	{
		if (modelRecap == null || socsPrepCopy == null) { if (modelRecap != null) modelRecap.setRowCount(0); return; }
		modelRecap.setRowCount(0);
		for (Societe soc : socsPrepCopy)
			for (Lot l : soc.getLots())
			{
				Ace ace = getAceDuLotDansSoc(l, soc);
				modelRecap.addRow(new Object[]{
					l.getNumCDE(), soc.getNom(), ace != null ? ace.getNom() : "—"
				});
			}
	}

	// ═════════════════════════════════════════════════════════════════════
	//  COMBOS
	// ═════════════════════════════════════════════════════════════════════

	private void remplirCombSoc()
	{
		if (combSoc == null) return;
		combSoc.removeAllItems();
		combSoc.addItem("— Société —");
		if (socsPrepCopy != null) for (Societe s : socsPrepCopy) combSoc.addItem(s.getNom());
		remplirCombAce();
	}

	private void remplirCombAce()
	{
		if (combAce == null) return;
		combAce.removeAllItems();
		combAce.addItem("— ACE —");
		int idx = combSoc != null ? combSoc.getSelectedIndex() - 1 : -1;
		if (idx < 0 || socsPrepCopy == null || idx >= socsPrepCopy.size()) return;
		for (Ace a : socsPrepCopy.get(idx).getAces()) combAce.addItem(a.getNom());
	}

	private void majEtat()
	{
		if (lblEtat == null) return;
		if (lotsPrep == null)
		{
			lblEtat.setText("<html><i>Aucune semaine préparée — importez un fichier Excel dans l'onglet « Semaine suivante ».</i></html>");
			return;
		}
		int n = compterAffectes(socsPrepCopy);
		lblEtat.setText("<html><span style='color:#16a34a'>✓ "
			+ lotsPrep.size() + " lots — " + n + " pré-affectés</span></html>");
	}

	// ═════════════════════════════════════════════════════════════════════
	//  HELPERS MÉTIER
	// ═════════════════════════════════════════════════════════════════════

	private ArrayList<Societe> copierSocietesVides()
	{
		ArrayList<Societe> socs  = serveur.getSocietes();
		ArrayList<Societe> copie = new ArrayList<>();
		if (socs == null) return copie;
		for (Societe s : socs)
		{
			Societe c = new Societe(s.getNom(), s.getCe(), new ArrayList<>(), s.getTotalHeuresCE());
			for (Ace a : s.getAces())
				c.getAces().add(new Ace(a.getNom(), a.getNbPers(), a.getEffectifActuel(), 0));
			copie.add(c);
		}
		return copie;
	}

	private Societe getSocDuLot(Lot lot)
	{
		if (lot == null || socsPrepCopy == null) return null;
		for (Societe s : socsPrepCopy)
			for (Lot l : s.getLots())
				if (l != null && lot.getId() != null && lot.getId().equals(l.getId())) return s;
		return null;
	}

	private Ace getAceDuLot(Lot lot)
	{
		if (lot == null || socsPrepCopy == null) return null;
		for (Societe s : socsPrepCopy)
			for (Ace a : s.getAces())
				for (Lot l : a.getLots())
					if (l != null && lot.getId() != null && lot.getId().equals(l.getId())) return a;
		return null;
	}

	private Ace getAceDuLotDansSoc(Lot lot, Societe soc)
	{
		if (lot == null || soc == null) return null;
		for (Ace a : soc.getAces())
			for (Lot l : a.getLots())
				if (l != null && lot.getId() != null && lot.getId().equals(l.getId())) return a;
		return null;
	}

	private int compterAffectes(ArrayList<Societe> socs)
	{
		if (socs == null) return 0;
		int n = 0; for (Societe s : socs) n += s.getLots().size(); return n;
	}

	private static String safe(String s)  { return s != null ? s : ""; }
	private static String trunc(String s, int max)
	{ return s == null ? "—" : s.length() <= max ? s : s.substring(0, max) + "…"; }
}
