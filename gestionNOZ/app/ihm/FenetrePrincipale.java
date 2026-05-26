package app.ihm;

import app.ControleurClient;
import app.IControleur;
import app.ihm.diagrame.*;
import app.ihm.ficheroute.PanelFicheRoute;
import app.ihm.gestionlot.PanelAffectation;
import app.ihm.gestionlot.PanelLots;
import app.ihm.gestionlot.PanelSocietes;
import app.ihm.map.PanelMap;
import app.metier.PlanningGlobal;
import app.metier.lot.Lot;
import java.awt.BorderLayout;
import java.awt.Button;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

/**
 * Fenêtre principale.
 *
 * Permissions selon le mode :
 *   Solo (Controleur)        → accès complet
 *   Client PAM               → accès complet + bouton désynchronisation
 *   Client société (ex: EUP) → PanelAffectation masqué, Heures Sup bloqué
 *
 * Heures sup : géré uniquement par le serveur (FenetreServeur).
 *   → bouton retiré du client (affiché en lecture seule dans l'en-tête).
 *
 * Mode désynchronisé (PAM uniquement) :
 *   → polling stoppé, menu Fichier réactivé pour préparer des semaines futures.
 */
public class FenetrePrincipale extends JFrame
{
	private final IControleur ctrl;

	private PanelAffectation panelAffectation;
	private PanelSocietes    panelSocietes;
	private PanelLots        panelLots;
	private PanelFicheRoute  panelFicheRoute;
	private PanelMap         panelMap;
	private PanelDiagrame    panelAuto;
	private JLabel           lblInfo;

	// Bandeau d'état en haut (visible en mode client)
	private JPanel  bandeauEtat;
	private JLabel  lblBandeauTexte;
	private JButton btnDesync;
	private JButton btnResync;

	// Items de menu contrôlés
	private JMenuItem itemOuvrir;
	private JMenuItem itemNouveaux;

	// ── Constructeur ─────────────────────────────────────────────────────

	public FenetrePrincipale(IControleur ctrl)
	{
		this.ctrl = ctrl;
		setTitle("Planning Global Futura — PAM" + titreComplement());
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setSize(1480, 780);
		setExtendedState(JFrame.MAXIMIZED_BOTH);
		setLocationRelativeTo(null);
		setLayout(new BorderLayout());
		getContentPane().setBackground(IhmUtils.FOND);

		setJMenuBar(creerMenuBar());
		add(creerEntete(), BorderLayout.NORTH);

		// Bandeau état client (PAM ou société)
		if (estModeClient())
		{
			bandeauEtat = creerBandeauEtat();
			add(bandeauEtat, BorderLayout.AFTER_LAST_LINE);
		}

		this.panelAffectation = new PanelAffectation(ctrl, this);
		this.panelSocietes    = new PanelSocietes   (ctrl, this);
		this.panelLots        = new PanelLots       (ctrl, this);
		this.panelFicheRoute  = new PanelFicheRoute (ctrl, this);
		this.panelMap         = new PanelMap        (ctrl, this);
		this.panelAuto        = new PanelDiagrame   (ctrl, this);

		JTabbedPane onglets = new JTabbedPane();
		onglets.setFont(new Font("SansSerif", Font.PLAIN, 13));

		// PanelAffectation masqué si client société (non PAM)
		if (!estModeClient() || clientPAM())
			onglets.addTab("⊕ Affectation", panelAffectation);

		onglets.addTab("📋 Fiches de Route",   panelFicheRoute);
		onglets.addTab("☰ Liste des lots",    panelLots);
		onglets.addTab("🕒 Sociétés & heures", panelSocietes);
		onglets.addTab("🗺 Carte entrepôt",    panelMap);
		onglets.addTab("⚙ DiagrameGantt",     panelAuto);
		add(onglets, BorderLayout.CENTER);

		onglets.addChangeListener(e -> {
			if (onglets.getSelectedComponent() == panelFicheRoute)
				panelFicheRoute.rafraichir();
			if (onglets.getSelectedComponent() == panelMap)
				panelMap.rafraichir();
		});

		if (!estModeClient() || clientPAM())
			panelAffectation.remplirComboSocietes();

		rafraichirTout();
		setVisible(true);
	}

	// ── Bandeau état client ───────────────────────────────────────────────

	private JPanel creerBandeauEtat()
	{
		JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
		p.setBackground(new Color(30, 60, 120));

		lblBandeauTexte = new JLabel();
		lblBandeauTexte.setFont(new Font("SansSerif", Font.PLAIN, 12));
		lblBandeauTexte.setForeground(new Color(200, 220, 255));
		p.add(lblBandeauTexte);

		// Bouton désync — PAM uniquement
		if (clientPAM())
		{
			btnDesync = new JButton("🔓 Se désynchroniser");
			btnDesync.setFont(new Font("SansSerif", Font.BOLD, 11));
			btnDesync.setBackground(new Color(180, 120, 20));
			btnDesync.setForeground(Color.WHITE);
			btnDesync.setFocusPainted(false);
			btnDesync.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 10));
			btnDesync.setToolTipText("Préparer des semaines futures sans affecter les clients");
			btnDesync.addActionListener(e -> seDesynchroniser());
			p.add(btnDesync);

			btnResync = new JButton("🔒 Se resynchroniser");
			btnResync.setFont(new Font("SansSerif", Font.BOLD, 11));
			btnResync.setBackground(new Color(40, 140, 60));
			btnResync.setForeground(Color.WHITE);
			btnResync.setFocusPainted(false);
			btnResync.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 10));
			btnResync.setToolTipText("Revenir aux données du serveur");
			btnResync.addActionListener(e -> seResynchroniser());
			btnResync.setVisible(false);
			p.add(btnResync);
		}

		majTextureBandeau(p);
		return p;
	}

	/** Met à jour le texte et les boutons du bandeau selon l'état courant. */
	public void majBandeauEtat()
	{
		if (bandeauEtat == null) return;
		ControleurClient cc = (ControleurClient) ctrl;
		majTextureBandeau(bandeauEtat);
		majMenuFichier();
		bandeauEtat.revalidate();
		bandeauEtat.repaint();
	}

	private void majTextureBandeau(JPanel p)
	{
		if (lblBandeauTexte == null) return;
		ControleurClient cc = (ControleurClient) ctrl;
		boolean desync = cc.isDesynchronise();

		if (desync)
		{
			p.setBackground(new Color(100, 60, 10));
			lblBandeauTexte.setText(
				"⚠  Mode DÉSYNCHRONISÉ — Vous travaillez en local. Les clients ne voient pas vos modifications.");
			if (btnDesync != null) btnDesync.setVisible(false);
			if (btnResync != null) btnResync.setVisible(true);
		}
		else
		{
			p.setBackground(new Color(30, 60, 120));
			String id = cc.getIdentifiant();
			if (cc.isAccesPAM())
				lblBandeauTexte.setText("🔒  Connecté en tant que PAM — Synchronisé avec le serveur");
			else
				lblBandeauTexte.setText("🔒  Connecté en tant que " + id + " — Accès limité (lecture / suivi)");
			if (btnDesync != null) btnDesync.setVisible(true);
			if (btnResync != null) btnResync.setVisible(false);
		}
	}

	// ── Actions désync ────────────────────────────────────────────────────

	private void seDesynchroniser()
	{
		int r = JOptionPane.showConfirmDialog(this,
			"Se désynchroniser du serveur ?\n\n"
				+ "Vous pourrez préparer des semaines futures localement.\n"
				+ "Les autres clients ne seront pas affectés.",
			"Désynchronisation", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
		if (r != JOptionPane.YES_OPTION) return;
		((ControleurClient) ctrl).seDesynchroniser();
		majBandeauEtat();
		majMenuFichier();
	}

	private void seResynchroniser()
	{
		int r = JOptionPane.showConfirmDialog(this,
			"Se resynchroniser avec le serveur ?\n\n"
				+ "Vos modifications locales seront perdues.",
			"Resynchronisation", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
		if (r != JOptionPane.YES_OPTION) return;
		((ControleurClient) ctrl).seResynchroniser();
		majBandeauEtat();
		majMenuFichier();
	}

	// ── Menu Fichier ──────────────────────────────────────────────────────

	private JMenuBar creerMenuBar()
	{
		JMenuBar bar = new JMenuBar();
		JMenu menuFichier = new JMenu("Fichier");
		menuFichier.setFont(new Font("SansSerif", Font.PLAIN, 13));

		itemOuvrir      = new JMenuItem("📂  Charger une sauvegarde…");
		JMenuItem itemSauvegarder = new JMenuItem("💾  Sauvegarder      Ctrl+S");
		itemNouveaux    = new JMenuItem("🆕  Nouveaux fichiers JSON…");

		itemOuvrir     .addActionListener(e -> ouvrirSauvegarde());
		itemSauvegarder.addActionListener(e -> sauvegarder());
		itemNouveaux   .addActionListener(e -> nouveaux());

		itemOuvrir     .setAccelerator(KeyStroke.getKeyStroke("ctrl O"));
		itemSauvegarder.setAccelerator(KeyStroke.getKeyStroke("ctrl S"));
		itemNouveaux   .setAccelerator(KeyStroke.getKeyStroke("ctrl N"));

		// En mode client synchronisé, désactiver charger/nouveaux
		if (estModeClient())
		{
			itemOuvrir .setEnabled(false);
			itemNouveaux.setEnabled(false);
			String tip = "Disponible en mode désynchronisé (bouton dans la barre d'état)";
			itemOuvrir .setToolTipText(tip);
			itemNouveaux.setToolTipText(tip);
		}

		menuFichier.add(itemOuvrir);
		menuFichier.addSeparator();
		menuFichier.add(itemSauvegarder);
		menuFichier.addSeparator();
		menuFichier.add(itemNouveaux);
		bar.add(menuFichier);
		return bar;
	}

	/** Met à jour l'état actif/inactif du menu selon synchro. */
	private void majMenuFichier()
	{
		if (itemOuvrir == null || itemNouveaux == null) return;
		if (!estModeClient()) return;
		boolean desync = ((ControleurClient) ctrl).isDesynchronise();
		itemOuvrir  .setEnabled(desync);
		itemNouveaux.setEnabled(desync);
	}

	// ── Actions menu ──────────────────────────────────────────────────────

	private void ouvrirSauvegarde()
	{
		if (estModeClient() && !((ControleurClient) ctrl).isDesynchronise())
		{
			JOptionPane.showMessageDialog(this,
				"⛔  Désynchronisez-vous d'abord pour charger des données locales.",
				"Action non autorisée", JOptionPane.WARNING_MESSAGE);
			return;
		}

		JFileChooser fc = new JFileChooser();
		fc.setDialogTitle("Ouvrir une sauvegarde JSON");
		fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;

		String dossier = fc.getSelectedFile().getAbsolutePath();
		try
		{
			ctrl.chargerDonnees(dossier);
			if (panelAffectation != null) panelAffectation.remplirComboSocietes();
			panelFicheRoute.remplirComboSocietes();
			JOptionPane.showMessageDialog(this,
				"Sauvegarde chargée : " + fc.getSelectedFile().getName(),
				"Chargement OK", JOptionPane.INFORMATION_MESSAGE);
		}
		catch (Exception ex)
		{
			JOptionPane.showMessageDialog(this, "Erreur : " + ex.getMessage(),
				"Erreur", JOptionPane.ERROR_MESSAGE);
		}
	}

	private void sauvegarder()
	{
		JFileChooser fc = new JFileChooser();
		fc.setDialogTitle("Copier les fichiers JSON vers…");
		fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		fc.setAcceptAllFileFilterUsed(false);
		if (fc.showDialog(this, "Copier") != JFileChooser.APPROVE_OPTION) return;

		String dossier = fc.getSelectedFile().getAbsolutePath();
		String numSemaine = JOptionPane.showInputDialog(
			this, "Numéro de semaine :", "Sauvegarde — semaine", JOptionPane.PLAIN_MESSAGE);
		if (numSemaine == null || numSemaine.isBlank()) return;

		try
		{
			ctrl.sauvegarderDonnees(dossier, numSemaine.trim());
			JOptionPane.showMessageDialog(this,
				"Fichiers copiés vers : " + fc.getSelectedFile().getName(),
				"Sauvegarde OK", JOptionPane.INFORMATION_MESSAGE);
		}
		catch (Exception ex)
		{
			JOptionPane.showMessageDialog(this, "Erreur : " + ex.getMessage(),
				"Erreur", JOptionPane.ERROR_MESSAGE);
		}
	}

	public void nouveaux()
	{
		if (estModeClient() && !((ControleurClient) ctrl).isDesynchronise())
		{
			JOptionPane.showMessageDialog(this,
				"⛔  Désynchronisez-vous d'abord pour importer un nouveau fichier Excel.",
				"Action non autorisée", JOptionPane.WARNING_MESSAGE);
			return;
		}

		int res = JOptionPane.showConfirmDialog(this,
			"Voulez-vous vraiment réinitialiser les données ?",
			"Nouvelle session", JOptionPane.YES_NO_OPTION);
		if (res == JOptionPane.YES_OPTION)
		{
			ctrl.nouveaux();
			if (panelAffectation != null) panelAffectation.remplirComboSocietes();
			panelFicheRoute.remplirComboSocietes();
			rafraichirTout();
		}
	}

	// ── En-tête ───────────────────────────────────────────────────────────

	private JPanel creerEntete()
	{
		JPanel p = new JPanel(new BorderLayout());
		p.setBackground(IhmUtils.HEADER);
		p.setBorder(BorderFactory.createEmptyBorder(10, 18, 10, 18));

		JLabel titre = new JLabel("Planning Global Futura — Gestion des lots");
		titre.setForeground(Color.WHITE);
		titre.setFont(new Font("SansSerif", Font.BOLD, 17));

		Button btnRafraichir = new Button("⟳");
		btnRafraichir.setFont(new Font("SansSerif", Font.PLAIN, 12));
		btnRafraichir.setBackground(IhmUtils.HEADER);
		btnRafraichir.setForeground(new Color(255, 255, 180));
		btnRafraichir.addActionListener(e -> this.rafraichirTout());

		// Bouton Heures Sup : visible seulement en mode solo
		Button btnSup = new Button("Heures Sup");
		btnSup.setFont(new Font("SansSerif", Font.PLAIN, 12));
		btnSup.setBackground(IhmUtils.HEADER);
		btnSup.setForeground(new Color(255, 255, 180));
		btnSup.setEnabled(!estModeClient()); // désactivé pour les clients
		btnSup.addActionListener(e -> this.SemaineSup());
		if (estModeClient())
			btnSup.setLabel("Heures Sup (serveur)");

		lblInfo = new JLabel(buildInfo());
		lblInfo.setForeground(new Color(180, 180, 180));
		lblInfo.setFont(new Font("SansSerif", Font.PLAIN, 12));

		JPanel panelBtn = new JPanel(new FlowLayout(FlowLayout.LEFT));
		panelBtn.setBackground(IhmUtils.HEADER);
		panelBtn.add(btnSup);
		panelBtn.add(btnRafraichir);

		p.add(titre,    BorderLayout.WEST);
		p.add(lblInfo,  BorderLayout.EAST);
		p.add(panelBtn, BorderLayout.SOUTH);

		return p;
	}

	private String buildInfo()
	{
		long nbAff = ctrl.getSocietes().stream().mapToLong(s -> s.getLots().size()).sum();
		int  nbH   = ctrl.getSocietes().stream().mapToInt(s -> s.getTotalHeuresCE()).sum();
		String heureSup = PlanningGlobal.estHeureSup ? "oui" : "non";
		String desyncInfo = (estModeClient() && ((ControleurClient) ctrl).isDesynchronise())
			? "  |  ⚠ DÉSYNC" : "";
		return ctrl.getLots().size() + " lots  |  " + " Heures total Lot" + getHeureLotTotal()
			+ ctrl.getSocietes().size() + " sociétés  |  "
			+ nbAff + " affectés  |  "
			+ nbH + "h disponibles  |  Heures Sup : " + heureSup
			+ desyncInfo;
	}

	public String getHeureLotTotal()
	{
		int total = 0;
		for (Lot lot : ctrl.getLots())
		{
			if (!lot.getStatut().contains("bloqué") && !lot.isEstSousDouane())
			{
				if (ctrl.getSocieteDuLot(lot) == null)
					total += lot.getHeures();
			}
		}
		return total > 0 ? " (" + total + "h)  |  " : "  |  ";
	}

	// ── Rafraîchissement global ───────────────────────────────────────────

	public void rafraichirTout()
	{
		Runnable refresh = () -> {
			if (panelAffectation != null) panelAffectation.rafraichir();
			this.panelSocietes   .rafraichir();
			this.panelLots       .rafraichir();
			this.panelFicheRoute .rafraichir();
			this.panelMap        .rafraichir();
			if (lblInfo != null) lblInfo.setText(buildInfo());
		};
		if (SwingUtilities.isEventDispatchThread())
		{
			refresh.run();
		}
		else
		{
			SwingUtilities.invokeLater(refresh);
		}
		if (!estModeClient() || !((ControleurClient) ctrl).isDesynchronise())
			this.ctrl.autoSauvegarde();
	}

	public void SemaineSup()
	{
		if (estModeClient())
		{
			JOptionPane.showMessageDialog(this,
				"Les heures supplémentaires sont gérées depuis le panneau de contrôle du serveur.",
				"Information", JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		ctrl.semaineSup();
		rafraichirTout();
	}

	// ── Utilitaires ──────────────────────────────────────────────────────

	private boolean estModeClient() { return ctrl instanceof ControleurClient; }
	private boolean clientPAM()
	{
		return estModeClient() && ((ControleurClient) ctrl).isAccesPAM();
	}

	private String titreComplement()
	{
		if (!estModeClient()) return "";
		ControleurClient cc = (ControleurClient) ctrl;
		return cc.isAccesPAM() ? " [PAM]" : " [" + cc.getIdentifiant() + "]";
	}

	public PanelAffectation getPanelAffectation() { return panelAffectation; }
}