package app.ihm.serveur;

import app.ServeurHTTP;
import app.securite.GestionComptes;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.net.*;
import java.util.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

/**
 * ══════════════════════════════════════════════════════════════
 *  FenetreServeur — v4 avec demandes de comptes + semaine suivante
 *
 *  Nouveautés :
 *   - Onglet "Demandes" : approuver / refuser les comptes en attente
 *     (badge rouge si demandes présentes — identique au web)
 *   - Bouton "Semaine suivante" → ouvre PanelSemaineSuivante
 *   - Port affiché corrigé : 8082 partout
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

	// ── Palette ───────────────────────────────────────────────────────────
	private static final Color C_BG      = new Color(15, 17, 26);
	private static final Color C_SURFACE = new Color(24, 27, 40);
	private static final Color C_CARD    = new Color(30, 34, 50);
	private static final Color C_BORDER  = new Color(50, 55, 78);
	private static final Color C_BLUE    = new Color(64, 128, 230);
	private static final Color C_GREEN   = new Color(38, 168, 90);
	private static final Color C_ORANGE  = new Color(210, 140, 30);
	private static final Color C_RED     = new Color(210, 65, 65);
	private static final Color C_TEXT    = new Color(215, 220, 235);
	private static final Color C_MUTED   = new Color(120, 128, 155);
	private static final Color C_ACCENT  = new Color(100, 160, 255);

	// ── Onglets ───────────────────────────────────────────────────────────
	private JTabbedPane tabs;
	private JPanel      panelDemandesContenu;
	private JLabel      lblBadgeDemandes;
	private PanelSemaineSuivante panelSemSuiv;

	// ══════════════════════════════════════════════════════════════════════
	//  CONSTRUCTEUR
	// ══════════════════════════════════════════════════════════════════════

	public FenetreServeur(ServeurHTTP serveur)
	{
		this.serveur = serveur;

		setTitle("Serveur Planning Global Futura — Panneau de contrôle");
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

		setSize(820, 650);
		setMinimumSize(new Dimension(600, 500));
		setLocationRelativeTo(null);
		setResizable(true);
		getContentPane().setBackground(C_BG);
		setLayout(new BorderLayout(0, 0));

		add(buildHeader(), BorderLayout.NORTH);
		add(buildTabs(),   BorderLayout.CENTER);
		add(buildFooter(), BorderLayout.SOUTH);

		// Timer de rafraîchissement toutes les 2s — javax.swing.Timer explicite
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
		p.setBackground(C_SURFACE);
		p.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 0, 1, 0, C_BORDER),
			new EmptyBorder(18, 24, 18, 24)));

		// Titre
		JPanel left = new JPanel();
		left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
		left.setOpaque(false);
		JLabel titre = new JLabel("Panneau de contrôle");
		titre.setFont(new Font("SansSerif", Font.BOLD, 20));
		titre.setForeground(C_TEXT);
		JLabel sous = new JLabel("Serveur Planning Global Futura");
		sous.setFont(new Font("SansSerif", Font.PLAIN, 12));
		sous.setForeground(C_MUTED);
		left.add(titre);
		left.add(Box.createVerticalStrut(3));
		left.add(sous);

		// Indicateurs
		JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 16, 0));
		right.setOpaque(false);

		// Semaine
		JPanel cardSem = buildIndicateur("Semaine active", "");
		lblSemaine = (JLabel) ((JPanel) cardSem.getComponent(1)).getComponent(0);
		right.add(cardSem);

		// Clients
		JPanel cardCli = buildIndicateur("Clients connectés", "0");
		lblClients = (JLabel) ((JPanel) cardCli.getComponent(1)).getComponent(0);
		right.add(cardCli);

		// Heures sup
		JPanel cardHS = buildIndicateur("Heures sup", "non");
		lblHeureSup = (JLabel) ((JPanel) cardHS.getComponent(1)).getComponent(0);
		right.add(cardHS);

		// Indicateur EN LIGNE
		JPanel live = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
		live.setOpaque(false);
		JLabel dot = new JLabel("●");
		dot.setFont(new Font("SansSerif", Font.PLAIN, 10));
		dot.setForeground(C_GREEN);
		JLabel lbl = new JLabel("EN LIGNE");
		lbl.setFont(new Font("SansSerif", Font.BOLD, 11));
		lbl.setForeground(C_GREEN);
		live.add(dot); live.add(lbl);
		right.add(live);

		p.add(left,  BorderLayout.WEST);
		p.add(right, BorderLayout.EAST);
		return p;
	}

	private JPanel buildIndicateur(String label, String valeur)
	{
		JPanel p = new JPanel();
		p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
		p.setBackground(C_CARD);
		p.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(C_BORDER),
			new EmptyBorder(6, 12, 6, 12)));

		JLabel lbl = new JLabel(label);
		lbl.setFont(new Font("SansSerif", Font.PLAIN, 10));
		lbl.setForeground(C_MUTED);
		lbl.setAlignmentX(Component.CENTER_ALIGNMENT);

		JPanel valPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
		valPanel.setOpaque(false);
		JLabel val = new JLabel(valeur);
		val.setFont(new Font("SansSerif", Font.BOLD, 18));
		val.setForeground(C_ACCENT);
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
		tabs = new JTabbedPane();
		tabs.setBackground(C_BG);
		tabs.setForeground(C_TEXT);
		tabs.setFont(new Font("SansSerif", Font.BOLD, 12));

		// Onglet Opérations
		tabs.addTab("⚙ Opérations", buildPanelOperations());

		// Onglet Semaine suivante
		panelSemSuiv = new PanelSemaineSuivante(serveur);
		tabs.addTab("📅 Semaine suivante", panelSemSuiv);

		// Onglet Demandes de comptes (avec badge)
		tabs.addTab("👤 Demandes", buildPanelDemandes());

		// Rafraîchir le panel semaine quand on clique dessus
		tabs.addChangeListener(e -> {
			if (tabs.getSelectedIndex() == 1 && panelSemSuiv != null)
				panelSemSuiv.chargerEtat();
			if (tabs.getSelectedIndex() == 2)
				rafraichirDemandes();
		});

		return tabs;
	}

	// ── Onglet Opérations ─────────────────────────────────────────────────

	private JScrollPane buildPanelOperations()
	{
		JPanel root = new JPanel();
		root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
		root.setBackground(C_BG);
		root.setBorder(new EmptyBorder(14, 16, 14, 16));

		root.add(buildCardGestion());
		root.add(Box.createRigidArea(new Dimension(0, 12)));
		root.add(buildCardSauvegarde());
		root.add(Box.createRigidArea(new Dimension(0, 12)));
		root.add(buildCardHeuresSup());

		JScrollPane scroll = new JScrollPane(root);
		scroll.setBorder(null);
		scroll.getViewport().setBackground(C_BG);
		return scroll;
	}

	private JPanel buildCardGestion()
	{
		JPanel card = buildCard();

		// Charger semaine
		card.add(buildActionRow("📂", "Charger une semaine archivée",
			"Recharge les données d'une semaine précédente (dossier S17/, S18/…).",
			"Charger…", C_BLUE, e -> chargerSemaine()));
		card.add(buildSeparateur());

		// Nouvelle semaine
		card.add(buildActionRow("🆕", "Nouvelle semaine (import Excel)",
			"Importe un fichier XLSX et réinitialise les données serveur.",
			"Importer…", C_ORANGE, e -> nouvelleSemaine()));

		return card;
	}

	private JPanel buildCardSauvegarde()
	{
		JPanel card = buildCard();
		card.add(buildActionRow("💾", "Sauvegarder la semaine courante",
			"Crée un snapshot dans app/data/enregistrementparsemaine/.",
			"Sauvegarder…", C_GREEN, e -> sauvegarder()));
		return card;
	}

	private JPanel buildCardHeuresSup()
	{
		JPanel card = buildCard();
		card.add(buildActionRow("⏱", "Activer / Désactiver les heures supplémentaires",
			"Tous les clients se synchronisent dans les 3 secondes.",
			"Basculer", C_ORANGE, e -> toggleHeuresSup()));
		return card;
	}

	// ── Onglet Demandes ───────────────────────────────────────────────────

	private JPanel buildPanelDemandes()
	{
		JPanel root = new JPanel(new BorderLayout());
		root.setBackground(C_BG);
		root.setBorder(new EmptyBorder(14, 16, 14, 16));

		// Titre + badge
		JPanel titreRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
		titreRow.setOpaque(false);
		JLabel titreLbl = new JLabel("🔔 Demandes de compte en attente");
		titreLbl.setFont(new Font("SansSerif", Font.BOLD, 14));
		titreLbl.setForeground(C_TEXT);
		lblBadgeDemandes = new JLabel("");
		lblBadgeDemandes.setFont(new Font("SansSerif", Font.BOLD, 11));
		lblBadgeDemandes.setForeground(Color.WHITE);
		lblBadgeDemandes.setOpaque(true);
		lblBadgeDemandes.setBackground(C_RED);
		lblBadgeDemandes.setBorder(new EmptyBorder(2, 7, 2, 7));
		lblBadgeDemandes.setVisible(false);
		titreRow.add(titreLbl);
		titreRow.add(lblBadgeDemandes);

		JLabel descLbl = new JLabel("<html><span style='color:#78809b'>"
			+ "Approuvez ou refusez chaque demande. "
			+ "Une fois approuvé, le compte peut se connecter immédiatement.</span></html>");
		descLbl.setFont(new Font("SansSerif", Font.PLAIN, 12));

		JPanel top = new JPanel();
		top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
		top.setOpaque(false);
		top.add(titreRow);
		top.add(Box.createRigidArea(new Dimension(0, 6)));
		top.add(descLbl);
		top.add(Box.createRigidArea(new Dimension(0, 14)));
		root.add(top, BorderLayout.NORTH);

		// Zone des demandes
		panelDemandesContenu = new JPanel();
		panelDemandesContenu.setLayout(new BoxLayout(panelDemandesContenu, BoxLayout.Y_AXIS));
		panelDemandesContenu.setBackground(C_BG);

		JScrollPane scroll = new JScrollPane(panelDemandesContenu);
		scroll.setBorder(null);
		scroll.getViewport().setBackground(C_BG);
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

		// Badge sur l'onglet
		if (lblBadgeDemandes != null)
		{
			lblBadgeDemandes.setText(nb > 0 ? String.valueOf(nb) : "");
			lblBadgeDemandes.setVisible(nb > 0);
		}

		// Badge sur le titre de l'onglet
		String titreOnglet = nb > 0 ? "👤 Demandes (" + nb + ")" : "👤 Demandes";
		if (tabs != null && tabs.getTabCount() > 2)
			tabs.setTitleAt(2, titreOnglet);

		if (nb == 0)
		{
			JLabel vide = new JLabel("Aucune demande en attente.");
			vide.setForeground(C_MUTED);
			vide.setFont(new Font("SansSerif", Font.PLAIN, 13));
			vide.setBorder(new EmptyBorder(10, 0, 0, 0));
			panelDemandesContenu.add(vide);
		}
		else
		{
			for (GestionComptes.DemandeCompte d : demandes)
			{
				panelDemandesContenu.add(buildLigneDemande(d));
				panelDemandesContenu.add(Box.createRigidArea(new Dimension(0, 6)));
			}
		}

		panelDemandesContenu.revalidate();
		panelDemandesContenu.repaint();
	}

	private JPanel buildLigneDemande(GestionComptes.DemandeCompte dem)
	{
		JPanel p = new JPanel(new BorderLayout(12, 0));
		p.setBackground(C_SURFACE);
		p.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(C_BORDER),
			new EmptyBorder(10, 14, 10, 14)));
		p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 60));

		// Info identifiant + date
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
		info.add(lblDate);
		p.add(info, BorderLayout.CENTER);

		// Boutons
		JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
		btns.setOpaque(false);

		JButton btnApprouver = new JButton("✓ Approuver");
		btnApprouver.setBackground(C_GREEN);
		btnApprouver.setForeground(Color.WHITE);
		btnApprouver.setFocusPainted(false);
		btnApprouver.setFont(new Font("SansSerif", Font.BOLD, 12));
		btnApprouver.setBorder(new EmptyBorder(6, 12, 6, 12));
		btnApprouver.addActionListener(e -> {
			GestionComptes.getInstance().approuver(dem.identifiant);
			JOptionPane.showMessageDialog(this,
				dem.identifiant + " est maintenant autorisé à se connecter.",
				"Compte approuvé", JOptionPane.INFORMATION_MESSAGE);
			rafraichirDemandes();
		});

		JButton btnRefuser = new JButton("✕ Refuser");
		btnRefuser.setBackground(new Color(60, 30, 30));
		btnRefuser.setForeground(new Color(230, 100, 100));
		btnRefuser.setFocusPainted(false);
		btnRefuser.setFont(new Font("SansSerif", Font.BOLD, 12));
		btnRefuser.setBorder(new EmptyBorder(6, 12, 6, 12));
		btnRefuser.addActionListener(e -> {
			int r = JOptionPane.showConfirmDialog(this,
				"Refuser la demande de \"" + dem.identifiant + "\" ?",
				"Confirmer", JOptionPane.YES_NO_OPTION);
			if (r != JOptionPane.YES_OPTION) return;
			GestionComptes.getInstance().refuser(dem.identifiant);
			rafraichirDemandes();
		});

		btns.add(btnApprouver);
		btns.add(btnRefuser);
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
			new EmptyBorder(10, 24, 10, 24)));

		JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
		left.setOpaque(false);
		JLabel dot = new JLabel("●");
		dot.setFont(new Font("SansSerif", Font.PLAIN, 10));
		dot.setForeground(C_GREEN);
		// Port corrigé : 8082
		JLabel txtServeur = new JLabel("Serveur actif — Port 8082");
		txtServeur.setFont(new Font("SansSerif", Font.PLAIN, 11));
		txtServeur.setForeground(C_MUTED);
		left.add(dot); left.add(txtServeur);

		lblIP = new JLabel("IP : " + detecterIP());
		lblIP.setFont(new Font("SansSerif", Font.BOLD, 11));
		lblIP.setForeground(C_ACCENT);

		p.add(left,  BorderLayout.WEST);
		p.add(lblIP, BorderLayout.EAST);
		return p;
	}

	// ══════════════════════════════════════════════════════════════════════
	//  ACTIONS
	// ══════════════════════════════════════════════════════════════════════

	private void chargerSemaine()
	{
		JFileChooser fc = new JFileChooser();
		fc.setDialogTitle("Sélectionner le dossier de sauvegarde (ex: S17)");
		fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		File def = new File("app/data/enregistrementparsemaine");
		if (def.exists()) fc.setCurrentDirectory(def);
		if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;

		int confirm = JOptionPane.showConfirmDialog(this,
			"Charger la semaine depuis :\n" + fc.getSelectedFile().getAbsolutePath()
				+ "\n\nTous les clients basculeront sur ces données.",
			"Confirmer le chargement", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
		if (confirm != JOptionPane.YES_OPTION) return;

		try
		{
			serveur.chargerSemaine(fc.getSelectedFile().getAbsolutePath());
			refresh();
			JOptionPane.showMessageDialog(this, "Semaine chargée. Les clients se synchroniseront dans les 3 secondes.",
				"Chargement OK", JOptionPane.INFORMATION_MESSAGE);
		}
		catch (Exception ex)
		{
			JOptionPane.showMessageDialog(this, "Erreur :\n" + ex.getMessage(), "Erreur", JOptionPane.ERROR_MESSAGE);
		}
	}

	private void nouvelleSemaine()
	{
		int confirm = JOptionPane.showConfirmDialog(this,
			"Charger un nouveau fichier Excel ?\nLes données actuelles restent en mémoire jusqu'à la sauvegarde.",
			"Nouvelle semaine", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
		if (confirm != JOptionPane.YES_OPTION) return;

		try
		{
			serveur.nouvelleSemaine(this);
			refresh();
		}
		catch (Exception ex)
		{
			JOptionPane.showMessageDialog(this, "Erreur :\n" + ex.getMessage(), "Erreur", JOptionPane.ERROR_MESSAGE);
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

		String numSemaine = JOptionPane.showInputDialog(this, "Numéro de semaine :", "Sauvegarde", JOptionPane.PLAIN_MESSAGE);
		if (numSemaine == null || numSemaine.isBlank()) return;

		try
		{
			serveur.sauvegarderSemaine(fc.getSelectedFile().getAbsolutePath(), numSemaine.trim());
			JOptionPane.showMessageDialog(this, "Sauvegarde effectuée dans S" + numSemaine.trim(),
				"Sauvegarde OK", JOptionPane.INFORMATION_MESSAGE);
		}
		catch (Exception ex)
		{
			JOptionPane.showMessageDialog(this, "Erreur :\n" + ex.getMessage(), "Erreur", JOptionPane.ERROR_MESSAGE);
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
	//  RAFRAÎCHISSEMENT
	// ══════════════════════════════════════════════════════════════════════

	private void refresh()
	{
		// Semaine
		String sem = serveur.getSemaineActive();
		lblSemaine.setText((sem != null && !sem.isBlank()) ? sem : "—");

		// Clients
		int nb = serveur.getNbClientsConnectes();
		lblClients.setText(String.valueOf(nb));
		lblClients.setForeground(nb > 0 ? C_GREEN : C_MUTED);

		// Heures sup
		boolean hs = app.metier.PlanningGlobal.estHeureSup;
		lblHeureSup.setText(hs ? "OUI" : "non");
		lblHeureSup.setForeground(hs ? C_ORANGE : C_MUTED);

		// Badge demandes (mis à jour en arrière-plan)
		SwingUtilities.invokeLater(() -> {
			int nbDem = GestionComptes.getInstance().getDemandesEnAttente().size();
			if (lblBadgeDemandes != null)
			{
				lblBadgeDemandes.setText(nbDem > 0 ? String.valueOf(nbDem) : "");
				lblBadgeDemandes.setVisible(nbDem > 0);
			}
			if (tabs != null && tabs.getTabCount() > 2)
				tabs.setTitleAt(2, nbDem > 0 ? "👤 Demandes (" + nbDem + ")" : "👤 Demandes");
		});
	}

	// ══════════════════════════════════════════════════════════════════════
	//  HELPERS UI
	// ══════════════════════════════════════════════════════════════════════

	private JPanel buildCard()
	{
		JPanel p = new JPanel();
		p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
		p.setBackground(C_CARD);
		p.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(C_BORDER, 1),
			new EmptyBorder(0, 0, 0, 0)));
		p.setAlignmentX(Component.LEFT_ALIGNMENT);
		p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 600));
		return p;
	}

	private JPanel buildSeparateur()
	{
		JPanel sep = new JPanel();
		sep.setBackground(C_BORDER);
		sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
		sep.setAlignmentX(Component.LEFT_ALIGNMENT);
		return sep;
	}

	private JPanel buildActionRow(String icone, String titre, String desc, String labelBtn, Color couleurBtn, ActionListener action)
	{
		JPanel row = new JPanel(new BorderLayout(16, 0));
		row.setOpaque(false);
		row.setBorder(new EmptyBorder(16, 18, 16, 18));
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 80));

		JPanel left = new JPanel();
		left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
		left.setOpaque(false);
		JPanel titreRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
		titreRow.setOpaque(false);
		JLabel ico = new JLabel(icone);
		ico.setFont(new Font("SansSerif", Font.PLAIN, 16));
		JLabel lblTitre = new JLabel(titre);
		lblTitre.setFont(new Font("SansSerif", Font.BOLD, 13));
		lblTitre.setForeground(C_TEXT);
		titreRow.add(ico); titreRow.add(lblTitre);
		JLabel lblDesc = new JLabel("<html><span style='color:#78809b'>" + desc + "</span></html>");
		lblDesc.setFont(new Font("SansSerif", Font.PLAIN, 11));
		left.add(titreRow);
		left.add(Box.createVerticalStrut(3));
		left.add(lblDesc);

		JButton btn = new JButton(labelBtn);
		btn.setBackground(couleurBtn);
		btn.setForeground(Color.WHITE);
		btn.setFocusPainted(false);
		btn.setFont(new Font("SansSerif", Font.BOLD, 12));
		btn.setBorder(new EmptyBorder(8, 16, 8, 16));
		btn.addActionListener(action);

		row.add(left, BorderLayout.CENTER);
		row.add(btn,  BorderLayout.EAST);
		return row;
	}

	private String detecterIP()
	{
		try
		{
			Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces();
			while (ifaces.hasMoreElements())
			{
				NetworkInterface ni = ifaces.nextElement();
				if (ni.isLoopback() || !ni.isUp()) continue;
				Enumeration<InetAddress> addrs = ni.getInetAddresses();
				while (addrs.hasMoreElements())
				{
					InetAddress addr = addrs.nextElement();
					if (addr instanceof Inet4Address && !addr.isLoopbackAddress())
						return addr.getHostAddress();
				}
			}
		}
		catch (Exception ignored) {}
		return "127.0.0.1";
	}
}