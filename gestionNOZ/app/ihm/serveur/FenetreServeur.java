package app.ihm.serveur;

import app.ServeurHTTP;
import app.securite.GestionComptes;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;
import java.io.File;
import java.net.*;
import java.util.*;
import javax.swing.*;
import javax.swing.border.*;

/**
 * ══════════════════════════════════════════════════════════════
 *  FenetreServeur — Refonte light moderne
 *
 *  CHANGEMENTS VISUELS :
 *  • Style light professionnel : fond blanc/gris très clair
 *  • Header épuré avec indicateurs en ligne (chips)
 *  • Cards blanches avec ombrage léger sur fond gris clair
 *  • Boutons arrondis colorés, lisibles
 *  • Onglets propres, sans surcharge
 *  • Footer minimaliste avec statut serveur + IP
 *  • Moins dense : marges généreuses, hiérarchie visuelle claire
 *
 *  LOGIQUE INCHANGÉE (100% compatible avec l'existant).
 * ══════════════════════════════════════════════════════════════
 */
public class FenetreServeur extends JFrame
{
	private final ServeurHTTP serveur;

	// ── Labels dynamiques ─────────────────────────────────────────────────
	private JLabel lblSemaine;
	private JLabel lblHeureSup;
	private JLabel lblClients;
	private JLabel lblIP;

	// ── Palette light ─────────────────────────────────────────────────────
	private static final Color C_BG       = new Color(246, 248, 251);   // fond général
	private static final Color C_SURFACE  = Color.WHITE;                 // cards
	private static final Color C_BORDER   = new Color(220, 226, 235);   // bordures légères
	private static final Color C_BORDER2  = new Color(200, 208, 220);   // bordures marquées
	private static final Color C_TEXT     = new Color(26,  32,  44);    // texte principal
	private static final Color C_MUTED    = new Color(100, 112, 132);   // texte secondaire
	private static final Color C_HEADER   = new Color(26,  32,  44);    // barre du haut
	private static final Color C_BLUE     = new Color(37,  99,  235);   // primaire
	private static final Color C_BLUE_L   = new Color(219, 234, 254);   // bg chip bleu
	private static final Color C_GREEN    = new Color(22,  163,  74);   // succès
	private static final Color C_GREEN_L  = new Color(220, 252, 231);   // bg chip vert
	private static final Color C_ORANGE   = new Color(217, 119,   6);   // avertissement
	private static final Color C_ORANGE_L = new Color(254, 243, 199);   // bg chip orange
	private static final Color C_RED      = new Color(220,  38,  38);   // danger
	private static final Color C_RED_L    = new Color(254, 226, 226);   // bg chip rouge

	// ── Onglets ───────────────────────────────────────────────────────────
	private JTabbedPane          tabs;
	private JPanel               panelDemandesContenu;
	private JLabel               lblBadgeDemandes;
	private PanelSemaineSuivante panelSemSuiv;

	// ══════════════════════════════════════════════════════════════════════
	//  CONSTRUCTEUR
	// ══════════════════════════════════════════════════════════════════════

	public FenetreServeur(ServeurHTTP serveur)
	{
		this.serveur = serveur;

		setTitle("Planning Global Futura — Panneau serveur");
		setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
		addWindowListener(new WindowAdapter()
		{
			@Override public void windowClosing(WindowEvent e)
			{
				int r = JOptionPane.showConfirmDialog(FenetreServeur.this,
					"Arrêter le serveur ?\nTous les clients seront déconnectés.",
					"Confirmation", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
				if (r == JOptionPane.YES_OPTION) System.exit(0);
			}
		});

		setSize(860, 680);
		setMinimumSize(new Dimension(640, 520));
		setLocationRelativeTo(null);
		setResizable(true);
		getContentPane().setBackground(C_BG);
		setLayout(new BorderLayout(0, 0));

		add(buildHeader(), BorderLayout.NORTH);
		add(buildTabs(),   BorderLayout.CENTER);
		add(buildFooter(), BorderLayout.SOUTH);

		javax.swing.Timer t = new javax.swing.Timer(2000, e -> refresh());
		t.setRepeats(true);
		t.start();

		setVisible(true);
		refresh();
	}

	// ══════════════════════════════════════════════════════════════════════
	//  HEADER
	// ══════════════════════════════════════════════════════════════════════

	private JPanel buildHeader()
	{
		JPanel p = new JPanel(new BorderLayout(0, 0));
		p.setBackground(C_HEADER);
		p.setBorder(new EmptyBorder(0, 0, 0, 0));

		// ── Ligne principale ──────────────────────────────────────────────
		JPanel ligne = new JPanel(new BorderLayout(16, 0));
		ligne.setBackground(C_HEADER);
		ligne.setBorder(new EmptyBorder(16, 24, 16, 24));

		// Gauche : icône + titre
		JPanel gauche = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		gauche.setOpaque(false);

		JLabel ico = new JLabel("⬡");
		ico.setFont(new Font("SansSerif", Font.BOLD, 20));
		ico.setForeground(new Color(96, 165, 250));
		ico.setBorder(new EmptyBorder(0, 0, 0, 12));

		JPanel titreBloc = new JPanel();
		titreBloc.setLayout(new BoxLayout(titreBloc, BoxLayout.Y_AXIS));
		titreBloc.setOpaque(false);

		JLabel titre = new JLabel("Panneau de contrôle");
		titre.setFont(new Font("SansSerif", Font.BOLD, 16));
		titre.setForeground(Color.WHITE);

		JLabel sous = new JLabel("Serveur Planning Global Futura");
		sous.setFont(new Font("SansSerif", Font.PLAIN, 11));
		sous.setForeground(new Color(148, 163, 184));

		titreBloc.add(titre);
		titreBloc.add(sous);
		gauche.add(ico);
		gauche.add(titreBloc);

		// Droite : indicateurs (chips)
		JPanel chips = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
		chips.setOpaque(false);

		// Semaine
		JPanel chipSem = buildChip("Semaine", "—", new Color(96, 165, 250), new Color(30, 58, 138));
		lblSemaine = (JLabel) ((JPanel) chipSem.getComponent(1)).getComponent(0);
		chips.add(chipSem);

		// Clients
		JPanel chipCli = buildChip("Clients", "0", new Color(52, 211, 153), new Color(6, 78, 59));
		lblClients = (JLabel) ((JPanel) chipCli.getComponent(1)).getComponent(0);
		chips.add(chipCli);

		// Heures sup
		JPanel chipHS = buildChip("Heures sup", "non", new Color(251, 191, 36), new Color(120, 53, 15));
		lblHeureSup = (JLabel) ((JPanel) chipHS.getComponent(1)).getComponent(0);
		chips.add(chipHS);

		// Point EN LIGNE
		JPanel live = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
		live.setOpaque(false);
		live.setBorder(new EmptyBorder(0, 8, 0, 0));
		JLabel dot = new JLabel("●");
		dot.setFont(new Font("SansSerif", Font.PLAIN, 9));
		dot.setForeground(new Color(52, 211, 153));
		JLabel txtLive = new JLabel("EN LIGNE · 8082");
		txtLive.setFont(new Font("SansSerif", Font.BOLD, 11));
		txtLive.setForeground(new Color(52, 211, 153));
		live.add(dot);
		live.add(txtLive);
		chips.add(live);

		ligne.add(gauche, BorderLayout.WEST);
		ligne.add(chips,  BorderLayout.EAST);

		// Trait séparateur en bas
		JPanel trait = new JPanel();
		trait.setBackground(new Color(51, 65, 85));
		trait.setPreferredSize(new Dimension(0, 1));

		p.add(ligne, BorderLayout.CENTER);
		p.add(trait, BorderLayout.SOUTH);
		return p;
	}

	/**
	 * Chip d'indicateur dans le header.
	 * Structure interne : [label (titre)] / [valPanel > [valeur]]
	 * → lblXxx = chip.getComponent(1) cast JPanel → getComponent(0) cast JLabel
	 */
	private JPanel buildChip(String titre, String valeur, Color couleurVal, Color bgChip)
	{
		JPanel p = new JPanel();
		p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
		p.setBackground(bgChip);
		p.setBorder(new EmptyBorder(6, 14, 6, 14));

		JLabel lbl = new JLabel(titre);
		lbl.setFont(new Font("SansSerif", Font.PLAIN, 9));
		lbl.setForeground(new Color(148, 163, 184));
		lbl.setAlignmentX(Component.CENTER_ALIGNMENT);

		JPanel valPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
		valPanel.setOpaque(false);
		JLabel val = new JLabel(valeur);
		val.setFont(new Font("SansSerif", Font.BOLD, 15));
		val.setForeground(couleurVal);
		valPanel.add(val);

		p.add(lbl);
		p.add(valPanel);
		return p;
	}

	// ══════════════════════════════════════════════════════════════════════
	//  ONGLETS
	// ══════════════════════════════════════════════════════════════════════

	private JTabbedPane buildTabs()
	{
		tabs = new JTabbedPane(JTabbedPane.TOP);
		tabs.setBackground(C_BG);
		tabs.setFont(new Font("SansSerif", Font.PLAIN, 13));
		tabs.setBorder(new EmptyBorder(0, 0, 0, 0));

		tabs.addTab("  Opérations  ",     buildPanelOperations());

		panelSemSuiv = new PanelSemaineSuivante(serveur);
		tabs.addTab("  Semaine suivante  ", panelSemSuiv);

		tabs.addTab("  Demandes  ",        buildPanelDemandes());

		tabs.addChangeListener(e -> {
			if (tabs.getSelectedIndex() == 1 && panelSemSuiv != null)
				panelSemSuiv.chargerEtat();
			if (tabs.getSelectedIndex() == 2)
				rafraichirDemandes();
		});

		return tabs;
	}

	// ══════════════════════════════════════════════════════════════════════
	//  ONGLET OPÉRATIONS
	// ══════════════════════════════════════════════════════════════════════

	private JScrollPane buildPanelOperations()
	{
		JPanel root = new JPanel();
		root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
		root.setBackground(C_BG);
		root.setBorder(new EmptyBorder(20, 24, 20, 24));

		// ── Section : Gestion des semaines ────────────────────────────────
		root.add(buildSectionTitre("Gestion des semaines"));
		root.add(Box.createRigidArea(new Dimension(0, 10)));

		JPanel cardSemaines = buildCard();
		cardSemaines.add(buildActionRow(
			"📂", "Charger une semaine archivée",
			"Recharge les données d'une semaine précédente (ex : S17/, S18/…).",
			"Charger…", C_BLUE, e -> chargerSemaine()));
		cardSemaines.add(buildSep());
		cardSemaines.add(buildActionRow(
			"🆕", "Nouvelle semaine — import Excel",
			"Importe un fichier XLSX et réinitialise les données du serveur.",
			"Importer…", C_ORANGE, e -> nouvelleSemaine()));
		root.add(cardSemaines);

		root.add(Box.createRigidArea(new Dimension(0, 20)));

		// ── Section : Sauvegarde ──────────────────────────────────────────
		root.add(buildSectionTitre("Sauvegarde"));
		root.add(Box.createRigidArea(new Dimension(0, 10)));

		JPanel cardSauvegarde = buildCard();
		cardSauvegarde.add(buildActionRow(
			"💾", "Sauvegarder la semaine courante",
			"Crée un snapshot archivé dans app/data/enregistrementparsemaine/.",
			"Sauvegarder…", C_GREEN, e -> sauvegarder()));
		root.add(cardSauvegarde);

		root.add(Box.createRigidArea(new Dimension(0, 20)));

		// ── Section : Paramètres ──────────────────────────────────────────
		root.add(buildSectionTitre("Paramètres"));
		root.add(Box.createRigidArea(new Dimension(0, 10)));

		JPanel cardParams = buildCard();
		cardParams.add(buildActionRow(
			"⏱", "Heures supplémentaires",
			"Active ou désactive le mode heures sup pour tous les clients (mise à jour en < 3 s).",
			"Basculer", C_ORANGE, e -> toggleHeuresSup()));
		root.add(cardParams);

		// Padding bas
		root.add(Box.createRigidArea(new Dimension(0, 20)));

		JScrollPane scroll = new JScrollPane(root);
		scroll.setBorder(null);
		scroll.getViewport().setBackground(C_BG);
		scroll.getVerticalScrollBar().setUnitIncrement(16);
		return scroll;
	}

	// ══════════════════════════════════════════════════════════════════════
	//  ONGLET DEMANDES
	// ══════════════════════════════════════════════════════════════════════

	private JPanel buildPanelDemandes()
	{
		JPanel root = new JPanel(new BorderLayout(0, 16));
		root.setBackground(C_BG);
		root.setBorder(new EmptyBorder(20, 24, 20, 24));

		// En-tête de section
		JPanel enTete = new JPanel();
		enTete.setLayout(new BoxLayout(enTete, BoxLayout.Y_AXIS));
		enTete.setOpaque(false);

		JPanel titreRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
		titreRow.setOpaque(false);
		JLabel titreLbl = new JLabel("Demandes de compte en attente");
		titreLbl.setFont(new Font("SansSerif", Font.BOLD, 15));
		titreLbl.setForeground(C_TEXT);

		lblBadgeDemandes = new JLabel("");
		lblBadgeDemandes.setFont(new Font("SansSerif", Font.BOLD, 10));
		lblBadgeDemandes.setForeground(Color.WHITE);
		lblBadgeDemandes.setOpaque(true);
		lblBadgeDemandes.setBackground(C_RED);
		lblBadgeDemandes.setBorder(new EmptyBorder(2, 7, 2, 7));
		lblBadgeDemandes.setVisible(false);

		titreRow.add(titreLbl);
		titreRow.add(lblBadgeDemandes);

		JLabel desc = new JLabel(
			"<html><span style='color:#64748b'>Une fois approuvé, le compte peut se connecter immédiatement.</span></html>");
		desc.setFont(new Font("SansSerif", Font.PLAIN, 12));

		enTete.add(titreRow);
		enTete.add(Box.createRigidArea(new Dimension(0, 4)));
		enTete.add(desc);

		// Zone des demandes
		panelDemandesContenu = new JPanel();
		panelDemandesContenu.setLayout(new BoxLayout(panelDemandesContenu, BoxLayout.Y_AXIS));
		panelDemandesContenu.setBackground(C_BG);

		JScrollPane scroll = new JScrollPane(panelDemandesContenu);
		scroll.setBorder(null);
		scroll.getViewport().setBackground(C_BG);
		scroll.getVerticalScrollBar().setUnitIncrement(16);

		root.add(enTete, BorderLayout.NORTH);
		root.add(scroll, BorderLayout.CENTER);

		rafraichirDemandes();
		return root;
	}

	private void rafraichirDemandes()
	{
		if (panelDemandesContenu == null) return;
		panelDemandesContenu.removeAll();

		java.util.List<GestionComptes.DemandeCompte> demandes =
			GestionComptes.getInstance().getDemandesEnAttente();
		int nb = demandes.size();

		if (lblBadgeDemandes != null)
		{
			lblBadgeDemandes.setText(nb > 0 ? String.valueOf(nb) : "");
			lblBadgeDemandes.setVisible(nb > 0);
		}
		if (tabs != null && tabs.getTabCount() > 2)
			tabs.setTitleAt(2, nb > 0 ? "  Demandes (" + nb + ")  " : "  Demandes  ");

		if (nb == 0)
		{
			JPanel vide = new JPanel(new FlowLayout(FlowLayout.LEFT));
			vide.setOpaque(false);
			JLabel lbl = new JLabel("✓  Aucune demande en attente.");
			lbl.setFont(new Font("SansSerif", Font.PLAIN, 13));
			lbl.setForeground(C_GREEN);
			vide.add(lbl);
			panelDemandesContenu.add(vide);
		}
		else
		{
			panelDemandesContenu.add(Box.createRigidArea(new Dimension(0, 8)));
			for (GestionComptes.DemandeCompte d : demandes)
			{
				panelDemandesContenu.add(buildLigneDemande(d));
				panelDemandesContenu.add(Box.createRigidArea(new Dimension(0, 8)));
			}
		}

		panelDemandesContenu.revalidate();
		panelDemandesContenu.repaint();
	}

	private JPanel buildLigneDemande(GestionComptes.DemandeCompte dem)
	{
		JPanel p = new JPanel(new BorderLayout(16, 0));
		p.setBackground(C_SURFACE);
		p.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(C_BORDER, 1),
			new EmptyBorder(14, 18, 14, 18)));
		p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 70));
		p.setAlignmentX(Component.LEFT_ALIGNMENT);

		// Info
		JPanel info = new JPanel();
		info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));
		info.setOpaque(false);

		JLabel lblId = new JLabel(dem.identifiant);
		lblId.setFont(new Font("SansSerif", Font.BOLD, 14));
		lblId.setForeground(C_TEXT);

		JLabel lblDate = new JLabel(dem.date);
		lblDate.setFont(new Font("SansSerif", Font.PLAIN, 11));
		lblDate.setForeground(C_MUTED);

		info.add(lblId);
		info.add(Box.createRigidArea(new Dimension(0, 2)));
		info.add(lblDate);
		p.add(info, BorderLayout.CENTER);

		// Boutons
		JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
		btns.setOpaque(false);

		JButton btnApp = buildBoutonAction("✓  Approuver", C_GREEN, Color.WHITE);
		btnApp.addActionListener(e -> {
			GestionComptes.getInstance().approuver(dem.identifiant);
			JOptionPane.showMessageDialog(this,
				dem.identifiant + " est maintenant autorisé à se connecter.",
				"Compte approuvé", JOptionPane.INFORMATION_MESSAGE);
			rafraichirDemandes();
		});

		JButton btnRef = buildBoutonAction("✕  Refuser", C_SURFACE, C_RED);
		btnRef.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(C_BORDER, 1),
			new EmptyBorder(6, 14, 6, 14)));
		btnRef.addActionListener(e -> {
			int r = JOptionPane.showConfirmDialog(this,
				"Refuser la demande de \"" + dem.identifiant + "\" ?",
				"Confirmer", JOptionPane.YES_NO_OPTION);
			if (r != JOptionPane.YES_OPTION) return;
			GestionComptes.getInstance().refuser(dem.identifiant);
			rafraichirDemandes();
		});

		btns.add(btnApp);
		btns.add(btnRef);
		p.add(btns, BorderLayout.EAST);
		return p;
	}

	// ══════════════════════════════════════════════════════════════════════
	//  FOOTER
	// ══════════════════════════════════════════════════════════════════════

	private JPanel buildFooter()
	{
		JPanel p = new JPanel(new BorderLayout());
		p.setBackground(C_SURFACE);
		p.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(1, 0, 0, 0, C_BORDER),
			new EmptyBorder(8, 24, 8, 24)));
		p.setPreferredSize(new Dimension(0, 34));

		// Gauche : statut
		JPanel gauche = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
		gauche.setOpaque(false);
		JLabel dot = new JLabel("●");
		dot.setFont(new Font("SansSerif", Font.PLAIN, 9));
		dot.setForeground(C_GREEN);
		JLabel txt = new JLabel("Serveur actif — Port 8082");
		txt.setFont(new Font("SansSerif", Font.PLAIN, 11));
		txt.setForeground(C_MUTED);
		gauche.add(dot);
		gauche.add(txt);

		// Droite : IP
		lblIP = new JLabel("IP  " + detecterIP());
		lblIP.setFont(new Font("SansSerif", Font.BOLD, 11));
		lblIP.setForeground(C_BLUE);

		p.add(gauche, BorderLayout.WEST);
		p.add(lblIP,  BorderLayout.EAST);
		return p;
	}

	// ══════════════════════════════════════════════════════════════════════
	//  RAFRAÎCHISSEMENT
	// ══════════════════════════════════════════════════════════════════════

	private void refresh()
	{
		// Semaine
		String sem = serveur.getSemaineActive();
		if (lblSemaine != null)
			lblSemaine.setText((sem != null && !sem.isBlank()) ? sem : "—");

		// Clients
		if (lblClients != null)
		{
			int nb = serveur.getNbClientsConnectes();
			lblClients.setText(String.valueOf(nb));
			lblClients.setForeground(nb > 0
				? new Color(52, 211, 153)
				: new Color(148, 163, 184));
		}

		// Heures sup
		if (lblHeureSup != null)
		{
			boolean hs = app.metier.PlanningGlobal.estHeureSup;
			lblHeureSup.setText(hs ? "OUI" : "non");
			lblHeureSup.setForeground(hs
				? new Color(251, 191, 36)
				: new Color(148, 163, 184));
		}

		// Badge demandes
		SwingUtilities.invokeLater(() -> {
			int nbDem = GestionComptes.getInstance().getDemandesEnAttente().size();
			if (lblBadgeDemandes != null)
			{
				lblBadgeDemandes.setText(nbDem > 0 ? String.valueOf(nbDem) : "");
				lblBadgeDemandes.setVisible(nbDem > 0);
			}
			if (tabs != null && tabs.getTabCount() > 2)
				tabs.setTitleAt(2, nbDem > 0
					? "  Demandes (" + nbDem + ")  "
					: "  Demandes  ");
		});
	}

	// ══════════════════════════════════════════════════════════════════════
	//  ACTIONS
	// ══════════════════════════════════════════════════════════════════════

	private void chargerSemaine()
	{
		JFileChooser fc = new JFileChooser();
		fc.setDialogTitle("Sélectionner le dossier de sauvegarde (ex : S17)");
		fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		File def = new File("app/data/enregistrementparsemaine");
		if (def.exists()) fc.setCurrentDirectory(def);
		if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;

		int confirm = JOptionPane.showConfirmDialog(this,
			"Charger depuis :\n" + fc.getSelectedFile().getAbsolutePath()
				+ "\n\nTous les clients basculeront sur ces données.",
			"Confirmer le chargement", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
		if (confirm != JOptionPane.YES_OPTION) return;

		try {
			serveur.chargerSemaine(fc.getSelectedFile().getAbsolutePath());
			refresh();
			JOptionPane.showMessageDialog(this,
				"Semaine chargée. Les clients se synchroniseront dans les 3 secondes.",
				"Chargement OK", JOptionPane.INFORMATION_MESSAGE);
		} catch (Exception ex) {
			JOptionPane.showMessageDialog(this,
				"Erreur :\n" + ex.getMessage(), "Erreur", JOptionPane.ERROR_MESSAGE);
		}
	}

	private void nouvelleSemaine()
	{
		int confirm = JOptionPane.showConfirmDialog(this,
			"Charger un nouveau fichier Excel ?\nLes données actuelles restent en mémoire jusqu'à la sauvegarde.",
			"Nouvelle semaine", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
		if (confirm != JOptionPane.YES_OPTION) return;

		try {
			serveur.nouvelleSemaine(this);
			refresh();
		} catch (Exception ex) {
			JOptionPane.showMessageDialog(this,
				"Erreur :\n" + ex.getMessage(), "Erreur", JOptionPane.ERROR_MESSAGE);
		}
	}

	private void sauvegarder()
	{
		JFileChooser fc = new JFileChooser();
		fc.setDialogTitle("Dossier de destination");
		fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		File def = new File("app/data/enregistrementparsemaine");
		if (def.exists()) fc.setCurrentDirectory(def);
		if (fc.showDialog(this, "Sauvegarder ici") != JFileChooser.APPROVE_OPTION) return;

		String numSemaine = JOptionPane.showInputDialog(
			this, "Numéro de semaine :", "Sauvegarde", JOptionPane.PLAIN_MESSAGE);
		if (numSemaine == null || numSemaine.isBlank()) return;

		try {
			serveur.sauvegarderSemaine(fc.getSelectedFile().getAbsolutePath(), numSemaine.trim());
			JOptionPane.showMessageDialog(this,
				"Sauvegarde effectuée dans S" + numSemaine.trim(),
				"Sauvegarde OK", JOptionPane.INFORMATION_MESSAGE);
		} catch (Exception ex) {
			JOptionPane.showMessageDialog(this,
				"Erreur :\n" + ex.getMessage(), "Erreur", JOptionPane.ERROR_MESSAGE);
		}
	}

	private void toggleHeuresSup()
	{
		serveur.toggleHeuresSup();
		boolean etat = app.metier.PlanningGlobal.estHeureSup;
		refresh();
		JOptionPane.showMessageDialog(this,
			"Heures supplémentaires : " + (etat ? "ACTIVÉES ✓" : "DÉSACTIVÉES"),
			"Heures sup", JOptionPane.INFORMATION_MESSAGE);
	}

	// ══════════════════════════════════════════════════════════════════════
	//  HELPERS UI
	// ══════════════════════════════════════════════════════════════════════

	/** Titre de section gris (au-dessus d'une card). */
	private JLabel buildSectionTitre(String texte)
	{
		JLabel l = new JLabel(texte.toUpperCase());
		l.setFont(new Font("SansSerif", Font.BOLD, 11));
		l.setForeground(C_MUTED);
		l.setAlignmentX(Component.LEFT_ALIGNMENT);
		return l;
	}

	/** Card blanche avec bordure légère. */
	private JPanel buildCard()
	{
		JPanel p = new JPanel();
		p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
		p.setBackground(C_SURFACE);
		p.setBorder(BorderFactory.createLineBorder(C_BORDER, 1));
		p.setAlignmentX(Component.LEFT_ALIGNMENT);
		p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 600));
		return p;
	}

	/** Ligne séparatrice fine à l'intérieur d'une card. */
	private JPanel buildSep()
	{
		JPanel s = new JPanel();
		s.setBackground(C_BORDER);
		s.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
		s.setAlignmentX(Component.LEFT_ALIGNMENT);
		return s;
	}

	/**
	 * Ligne d'action : icône + titre + description à gauche, bouton à droite.
	 */
	private JPanel buildActionRow(String icone, String titre, String desc,
			String labelBtn, Color couleurBtn, ActionListener action)
	{
		JPanel row = new JPanel(new BorderLayout(20, 0));
		row.setOpaque(false);
		row.setBorder(new EmptyBorder(18, 20, 18, 20));

		// Gauche : icône + textes
		JPanel gauche = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		gauche.setOpaque(false);

		JLabel ico = new JLabel(icone + "  ");
		ico.setFont(new Font("SansSerif", Font.PLAIN, 18));

		JPanel textes = new JPanel();
		textes.setLayout(new BoxLayout(textes, BoxLayout.Y_AXIS));
		textes.setOpaque(false);

		JLabel lblTitre = new JLabel(titre);
		lblTitre.setFont(new Font("SansSerif", Font.BOLD, 13));
		lblTitre.setForeground(C_TEXT);

		JLabel lblDesc = new JLabel(
			"<html><span style='color:#64748b'>" + desc + "</span></html>");
		lblDesc.setFont(new Font("SansSerif", Font.PLAIN, 11));

		textes.add(lblTitre);
		textes.add(Box.createRigidArea(new Dimension(0, 2)));
		textes.add(lblDesc);

		gauche.add(ico);
		gauche.add(textes);

		// Droite : bouton
		JButton btn = buildBoutonAction(labelBtn, couleurBtn, Color.WHITE);
		btn.addActionListener(action);

		row.add(gauche, BorderLayout.CENTER);
		row.add(btn,    BorderLayout.EAST);
		return row;
	}

	/** Bouton arrondi avec couleur pleine. */
	private JButton buildBoutonAction(String texte, Color bg, Color fg)
	{
		JButton b = new JButton(texte)
		{
			@Override protected void paintComponent(Graphics g)
			{
				Graphics2D g2 = (Graphics2D) g.create();
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
					RenderingHints.VALUE_ANTIALIAS_ON);
				g2.setColor(getModel().isRollover() ? bg.darker() : bg);
				g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 8, 8));
				g2.dispose();
				super.paintComponent(g);
			}
		};
		b.setForeground(fg);
		b.setFont(new Font("SansSerif", Font.BOLD, 12));
		b.setFocusPainted(false);
		b.setBorderPainted(false);
		b.setContentAreaFilled(false);
		b.setOpaque(false);
		b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		b.setBorder(new EmptyBorder(8, 18, 8, 18));
		return b;
	}

	private String detecterIP()
	{
		try {
			Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces();
			while (ifaces.hasMoreElements()) {
				NetworkInterface ni = ifaces.nextElement();
				if (ni.isLoopback() || !ni.isUp()) continue;
				Enumeration<InetAddress> addrs = ni.getInetAddresses();
				while (addrs.hasMoreElements()) {
					InetAddress addr = addrs.nextElement();
					if (addr instanceof Inet4Address && !addr.isLoopbackAddress())
						return addr.getHostAddress();
				}
			}
		} catch (Exception ignored) {}
		return "127.0.0.1";
	}
}