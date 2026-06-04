package app.ihm.map;

import app.IControleur;
import app.ihm.FenetrePrincipale;
import app.ihm.IhmUtils;
import app.metier.lot.Lot;
import app.metier.personelle.Societe;
import java.awt.*;
import java.util.*;
import java.util.List;
import javax.swing.*;

/**
 * Carte interactive de l'entrepôt.
 */
public class PanelMap extends JPanel
{
	private final IControleur       ctrl;
	private final FenetrePrincipale fenetre;

	private JTextArea         infoLot;
	private static final String[] ZONES_RANGEES   = { "A", "B", "C", "D" };
	private static final String[] ZONES_SPECIALES = { "LTS", "HD" };

	private static final Map<String, Color> COULEUR_ZONE = new LinkedHashMap<>();
	static {
		COULEUR_ZONE.put("A",   new Color(210, 230, 255));
		COULEUR_ZONE.put("B",   new Color(210, 255, 215));
		COULEUR_ZONE.put("C",   new Color(255, 235, 200));
		COULEUR_ZONE.put("D",   new Color(240, 215, 255));
		COULEUR_ZONE.put("LTS", new Color(200, 200, 200));
		COULEUR_ZONE.put("HD",  new Color(255, 210, 210));
	}

	private String    emplacementSel = null;
	private PlanPanel planPanel;
	private List<Lot> lotsCourants = new ArrayList<>();
	private Map<String, List<Lot>>    lotsParEmplacement = new HashMap<>();
	private Map<String, List<String>> numerosParZone     = new HashMap<>();

	private JLabel                   lblEmpl;
	private DefaultListModel<String> listModel;
	private JList<String>            listeLots;

	// ── Constructeur ──────────────────────────────────────────────────────

	public PanelMap(IControleur ctrl, FenetrePrincipale fenetre)
	{
		this.ctrl    = ctrl;
		this.fenetre = fenetre;
		setLayout(new BorderLayout(10, 0));
		setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
		setBackground(IhmUtils.FOND);

		construireCacheLots();
		planPanel = new PlanPanel();
		add(planPanel,          BorderLayout.CENTER);
		add(creerPanelDetail(), BorderLayout.EAST);
		add(creerLegende(),     BorderLayout.SOUTH);
	}

	// ── Plan Swing ────────────────────────────────────────────────────────

	private class PlanPanel extends JPanel
	{
		private final Map<String, JButton> emplacementButtons = new LinkedHashMap<>();

		PlanPanel()
		{
			setBackground(new Color(238, 240, 244));
			setPreferredSize(new Dimension(820, 560));
			setLayout(new GridBagLayout());
			rebuildPlan();
		}

		public void rebuildPlan()
		{
			removeAll();
			emplacementButtons.clear();

			JPanel topRow = new JPanel(new GridLayout(1, ZONES_RANGEES.length, 10, 10));
			topRow.setOpaque(false);
			for (String lettre : ZONES_RANGEES)
				topRow.add(creerZoneRangees(lettre));

			JPanel bottomRow = new JPanel(new GridLayout(1, ZONES_SPECIALES.length, 10, 10));
			bottomRow.setOpaque(false);
			for (String code : ZONES_SPECIALES)
				bottomRow.add(creerZoneSpeciale(code));

			GridBagConstraints gbc = new GridBagConstraints();
			gbc.gridx = 0; gbc.gridy = 0;
			gbc.weightx = 1.0; gbc.weighty = 1.0;
			gbc.fill = GridBagConstraints.BOTH;
			gbc.insets = new Insets(0, 0, 10, 0);
			add(topRow, gbc);

			gbc.gridy = 1; gbc.weighty = 0.3;
			add(bottomRow, gbc);

			revalidate();
			repaint();
		}

		public void updatePlan() { revalidate(); repaint(); }

		private JPanel creerZoneRangees(String lettre)
		{
			Color cFond = COULEUR_ZONE.getOrDefault(lettre, Color.LIGHT_GRAY);
			JPanel zone = new JPanel(new BorderLayout(0, 8));
			zone.setOpaque(true);
			zone.setBackground(cFond);
			zone.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(cFond.darker()),
				BorderFactory.createEmptyBorder(8, 8, 8, 8)));

			JLabel titre = new JLabel("Zone " + lettre);
			titre.setFont(new Font("SansSerif", Font.BOLD, 14));
			titre.setForeground(new Color(35, 40, 50));
			zone.add(titre, BorderLayout.NORTH);

			List<String> nums = getNumerosZone(lettre);
			if (nums.isEmpty())
			{
				JLabel vide = new JLabel("vide", SwingConstants.CENTER);
				vide.setFont(new Font("SansSerif", Font.ITALIC, 11));
				vide.setForeground(new Color(130, 135, 145));
				zone.add(vide, BorderLayout.CENTER);
			}
			else
			{
				int n    = nums.size();
				int cols = Math.max(1, (int) Math.ceil(Math.sqrt(n)));
				int rows = (int) Math.ceil((double) n / cols);
				JPanel grille = new JPanel(new GridLayout(rows, cols, 5, 5));
				grille.setOpaque(false);
				for (String num : nums)
				{
					String empl = lettre + num;
					JButton btn = creerBoutonEmplacement(empl);
					emplacementButtons.put(empl, btn);
					grille.add(btn);
				}
				zone.add(grille, BorderLayout.CENTER);
			}
			return zone;
		}

		private JPanel creerZoneSpeciale(String code)
		{
			Color cFond = COULEUR_ZONE.getOrDefault(code, Color.LIGHT_GRAY);
			JPanel zone = new JPanel(new BorderLayout(0, 8));
			zone.setOpaque(true);
			zone.setBackground(cFond);
			zone.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(cFond.darker()),
				BorderFactory.createEmptyBorder(8, 8, 8, 8)));

			String lib = code.equals("LTS") ? "LTS — Long Term Storage" : "HD — Hors Douane";
			JLabel titre = new JLabel(lib);
			titre.setFont(new Font("SansSerif", Font.BOLD, 12));
			titre.setForeground(new Color(35, 40, 50));
			zone.add(titre, BorderLayout.NORTH);

			JButton btn = creerBoutonEmplacement(code);
			emplacementButtons.put(code, btn);
			zone.add(btn, BorderLayout.CENTER);
			return zone;
		}

		private JButton creerBoutonEmplacement(String empl)
		{
			BoutonEmplacement btn = new BoutonEmplacement(empl, getLotsEmplacement(empl));
			btn.addActionListener(e -> {
				selectionnerEmplacement(empl);
				updatePlan();
			});
			btn.setToolTipText(buildTooltip(empl));
			btn.setBorderPainted(true);
			return btn;
		}
	}

	// ── Données ───────────────────────────────────────────────────────────

	private List<String> getNumerosZone(String lettre)
	{ return numerosParZone.getOrDefault(lettre, new ArrayList<>()); }

	private List<Lot> getLotsEmplacement(String empl)
	{ return lotsParEmplacement.getOrDefault(empl, new ArrayList<>()); }

	private String buildTooltip(String empl)
	{
		List<Lot> lots = getLotsEmplacement(empl);
		if (lots.isEmpty())
			return "<html><b>" + empl + "</b> &mdash; aucun lot</html>";
		StringBuilder sb = new StringBuilder("<html><b>Emplacement " + empl + "</b><hr>");
		for (Lot l : lots)
		{
			Societe soc = ctrl.getSocieteDuLot(l);
			sb.append("• <b>").append(l.getNumCDE()).append("</b>")
			  .append("  ").append(s(l.getTypologie()))
			  .append("  [").append(soc != null ? soc.getNom() : "—").append("]")
			  .append("<br>");
		}
		return sb.append("</html>").toString();
	}

	// ── Sélection ─────────────────────────────────────────────────────────

	private void selectionnerEmplacement(String empl)
	{
		emplacementSel = empl;
		List<Lot> lots = getLotsEmplacement(empl);
		lotsCourants = lots;
		planPanel.updatePlan();

		lblEmpl.setText("📦  " + empl + "  —  " + lots.size() + " lot(s)");
		listModel.clear();

		if (lots.isEmpty())
		{
			listModel.addElement("  (aucun lot ici)");
		}
		else
		{
			for (Lot l : lots)
			{
				Societe soc = ctrl.getSocieteDuLot(l);
				listModel.addElement(icone(l)
					+ " " + l.getNumCDE()
					+ "  " + s(l.getTypologie())
					+ "  [" + (soc != null ? soc.getNom() : "—") + "]"
					+ "  " + String.format("%.1fh", l.getHeures()));
			}
		}
	}

	// ── Rafraîchissement ──────────────────────────────────────────────────

	public void rafraichir()
	{
		construireCacheLots();
		planPanel.rebuildPlan();
		if (emplacementSel != null)
			selectionnerEmplacement(emplacementSel);
		planPanel.repaint();
	}

	/**
	 * Construit deux maps à partir de tous les lots :
	 *   • lotsParEmplacement : code emplacement → liste des lots qui y sont stockés
	 *   • numerosParZone     : lettre de zone   → numéros triés (ex: "A" → ["1","2","12"])
	 *
	 * Format d'un emplacement : lettre de zone (A/B/C/D) + numéro entier optionnel (ex: "A12").
	 * Un emplacement sans numéro (ex: "A") est indexé sous la clé "".
	 * Les codes spéciaux (LTS, HD) restent dans lotsParEmplacement mais pas dans numerosParZone.
	 */
	private void construireCacheLots()
	{
		lotsParEmplacement.clear();
		numerosParZone.clear();

		Map<String, Set<String>> tempNums = new HashMap<>();
		for (String lettre : ZONES_RANGEES)
			tempNums.put(lettre, new TreeSet<>(Comparator.comparingInt(a -> {
				try { return Integer.parseInt(a); } catch (Exception e) { return 0; }
			})));

		for (Lot l : ctrl.getLots())
		{
			String empl = s(l.getEmplacement());
			lotsParEmplacement.computeIfAbsent(empl, k -> new ArrayList<>()).add(l);

			if (!empl.isEmpty())
			{
				String lettre = empl.substring(0, 1);
				if (Arrays.asList(ZONES_RANGEES).contains(lettre))
				{
					Set<String> nums = tempNums.get(lettre);
					if (empl.equals(lettre))
						nums.add("");
					else if (empl.length() > lettre.length())
					{
						String reste = empl.substring(lettre.length());
						if (reste.matches("\\d+")) nums.add(reste);
					}
				}
			}
		}

		for (String lettre : ZONES_RANGEES)
			numerosParZone.put(lettre, new ArrayList<>(tempNums.get(lettre)));
	}

	// ── Panel détail ──────────────────────────────────────────────────────

	private JPanel creerPanelDetail()
	{
		JPanel p = new JPanel(new BorderLayout(0, 8));
		p.setBackground(Color.WHITE);
		p.setPreferredSize(new Dimension(270, 0));
		p.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(IhmUtils.BORD),
			BorderFactory.createEmptyBorder(12, 12, 12, 12)));

		lblEmpl = new JLabel("← Cliquez sur un emplacement");
		lblEmpl.setFont(new Font("SansSerif", Font.BOLD, 13));
		lblEmpl.setForeground(IhmUtils.BLEU);

		listModel = new DefaultListModel<>();
		listeLots = new JList<>(listModel);
		listeLots.setFont(new Font("Monospaced", Font.PLAIN, 11));
		listeLots.setSelectionBackground(IhmUtils.SEL);

		listeLots.addListSelectionListener(e -> {
			if (!e.getValueIsAdjusting())
			{
				int idx = listeLots.getSelectedIndex();
				if (idx >= 0 && idx < lotsCourants.size())
					afficherInfoLot(lotsCourants.get(idx));
				else
					infoLot.setText("");
			}
		});

		infoLot = new JTextArea(8, 20);
		infoLot.setEditable(false);
		infoLot.setFont(new Font("Monospaced", Font.PLAIN, 12));
		infoLot.setBackground(IhmUtils.INFO);
		infoLot.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
		JScrollPane scrollInfo = new JScrollPane(infoLot);
		scrollInfo.setMaximumSize(new Dimension(Integer.MAX_VALUE, 190));
		scrollInfo.setAlignmentX(Component.LEFT_ALIGNMENT);

		JScrollPane scroll = new JScrollPane(listeLots);
		scroll.setBorder(BorderFactory.createLineBorder(IhmUtils.BORD));

		p.add(lblEmpl,     BorderLayout.NORTH);
		p.add(scroll,      BorderLayout.CENTER);
		p.add(scrollInfo,  BorderLayout.SOUTH);
		return p;
	}

	// ── Légende ───────────────────────────────────────────────────────────

	private JPanel creerLegende()
	{
		JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 14, 5));
		p.setBackground(IhmUtils.FOND);
		p.add(pastille(new Color(50, 150, 60),   "VA — Validé"));
		p.add(pastille(new Color(205, 55, 55),   "BL — Bloqué"));
		p.add(pastille(new Color(190, 115, 15),  "EP — En attente"));
		p.add(pastille(new Color(170, 85, 195),  "Sous douane"));
		p.add(pastille(new Color(210, 212, 218), "Vide"));
		p.add(pastille(new Color(45, 105, 215),  "Sélectionné"));
		JLabel h = new JLabel("   Survolez pour le détail · Cliquez pour sélectionner");
		h.setFont(new Font("SansSerif", Font.ITALIC, 11));
		h.setForeground(Color.GRAY);
		p.add(h);
		return p;
	}

	private JPanel pastille(Color c, String label)
	{
		JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		p.setBackground(IhmUtils.FOND);
		JPanel sq = new JPanel();
		sq.setBackground(c);
		sq.setPreferredSize(new Dimension(13, 13));
		sq.setBorder(BorderFactory.createLineBorder(c.darker()));
		p.add(sq);
		JLabel l = new JLabel(label);
		l.setFont(new Font("SansSerif", Font.PLAIN, 11));
		p.add(l);
		return p;
	}

	// ── Utilitaires ───────────────────────────────────────────────────────

	private static String s(String v) { return v != null ? v : ""; }

	private static String icone(Lot l)
	{
		if (l.isEstSousDouane()) return "🟣";
		String st = s(l.getStatutEchant());
		if (st.startsWith("VA")) return "🟢";
		if (st.startsWith("BL")) return "🔴";
		return "🟡";
	}

	private void afficherInfoLot(Lot lot)
	{
		StringBuilder sb = new StringBuilder();
		sb.append("Numéro de commande: ").append(lot.getNumCDE()).append("\n");
		sb.append("Typologie: ").append(s(lot.getTypologie())).append("\n");
		sb.append("Affaire: ").append(s(lot.getAffaire())).append("\n");
		sb.append("Nombre de pièces: ").append(lot.getNbPieces()).append("\n");
		sb.append("Cadence: ").append(lot.getCadence()).append("\n");
		sb.append("Heures: ").append(String.format("%.2f", lot.getHeures())).append("\n");
		sb.append("Heures ACE: ").append(String.format("%.2f", lot.getHeuresAce())).append("\n");
		sb.append("Valeur de vente: ").append(lot.getValeurVente()).append("\n");
		sb.append("Prix unitaire: ").append(String.format("%.2f", lot.getPrixUnitaire())).append("\n");
		sb.append("Semaine: ").append(s(lot.getSemaine())).append("\n");
		sb.append("Priorité: ").append(lot.getPriorite()).append("\n");
		sb.append("Statut: ").append(s(lot.getStatut())).append("\n");
		sb.append("Statut échantillon: ").append(s(lot.getStatutEchant())).append("\n");
		sb.append("Lot à charge: ").append(s(lot.getLotACharge())).append("\n");
		sb.append("Sous douane: ").append(lot.isEstSousDouane() ? "Oui" : "Non").append("\n");
		sb.append("Date réception: ").append(s(lot.getDateReception())).append("\n");
		sb.append("Date paiement: ").append(s(lot.getDatePaiement())).append("\n");
		sb.append("Commentaire: ").append(s(lot.getCommentaire())).append("\n");
		sb.append("Emplacement: ").append(s(lot.getEmplacement())).append("\n");
		sb.append("Méthode: ").append(s(lot.getMethode() == null ? "" : lot.getMethode().getNom())).append("\n");
		sb.append("Distribution: ").append(s(lot.getDistribution())).append("\n");
		sb.append("Format carton: ").append(s(lot.getFormatCarton())).append("\n");
		sb.append("Machine: ").append(lot.estMachine() ? "Oui" : "Non").append("\n");
		Societe soc = ctrl.getSocieteDuLot(lot);
		sb.append("Société: ").append(soc != null ? soc.getNom() : "—").append("\n");
		infoLot.setText(sb.toString());
	}
}
