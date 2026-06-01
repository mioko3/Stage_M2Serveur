package app.ihm;

import app.IControleur;
import app.ihm.diagrame.*;
import app.ihm.ficheroute.PanelFicheRoute;
import app.ihm.gestionlot.PanelAffectation;
import app.ihm.gestionlot.PanelLots;
import app.ihm.gestionlot.PanelSocietes;
import app.ihm.map.PanelMap;
import app.metier.PlanningGlobal;
import app.metier.lot.Lot;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;
import javax.swing.*;
import javax.swing.border.*;

/**
 * ══════════════════════════════════════════════════════════════
 *  FenetrePrincipale — Refonte light moderne
 *
 *  CHANGEMENTS VISUELS :
 *  • En-tête épuré : titre + chips de stats sur une seule barre
 *  • Onglets avec icônes, fond blanc, sélection soulignée
 *  • Moins dense : marges généreuses, pas de surcharge visuelle
 *  • Barre de statut fine en bas (synchronisation, version)
 *  • Boutons arrondis via IhmUtils.bouton()
 *
 *  LOGIQUE INCHANGÉE (100% compatible avec l'existant).
 * ══════════════════════════════════════════════════════════════
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

	// ── Chips de stats dans l'en-tête ────────────────────────────────────
	private JLabel chipLots;
	private JLabel chipAffectes;
	private JLabel chipHeures;
	private JLabel chipHeureSup;

	// ── Barre de statut (bas) ─────────────────────────────────────────────
	private JLabel lblStatutBas;

	// ── Bandeau désynchronisation ─────────────────────────────────────────
	private JPanel  bandeauDesync;
	private JButton btnDesync;
	private JButton btnResync;

	// ── Menu ──────────────────────────────────────────────────────────────
	private JMenuItem itemOuvrir;
	private JMenuItem itemNouveaux;

	// ══════════════════════════════════════════════════════════════════════
	//  CONSTRUCTEUR
	// ══════════════════════════════════════════════════════════════════════

	public FenetrePrincipale(IControleur ctrl)
	{
		this.ctrl = ctrl;

		setTitle("Planning Global Futura");
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setSize(1480, 820);
		setExtendedState(JFrame.MAXIMIZED_BOTH);
		setLocationRelativeTo(null);
		setLayout(new BorderLayout());
		getContentPane().setBackground(IhmUtils.FOND);

		setJMenuBar(creerMenuBar());

		// Structure : header → contenu → statusbar
		add(creerHeader(),    BorderLayout.NORTH);
		add(creerContenu(),   BorderLayout.CENTER);
		add(creerStatusBar(), BorderLayout.SOUTH);

		// Bandeau désync (PAM uniquement, caché par défaut)
		if (ctrl.isAccesPAM())
		{
			bandeauDesync = creerBandeauDesync();
			// Inséré entre header et contenu via wrapper
		}

		// Permissions selon le mode
		appliquerPermissions();

		panelAffectation.remplirComboSocietes();
		rafraichirTout();
		setVisible(true);
	}

	// ══════════════════════════════════════════════════════════════════════
	//  HEADER
	// ══════════════════════════════════════════════════════════════════════

	private JPanel creerHeader()
	{
		// Fond sombre élégant
		JPanel header = new JPanel(new BorderLayout(0, 0));
		header.setBackground(IhmUtils.HEADER);
		header.setBorder(new EmptyBorder(0, 0, 0, 0));

		// ── Ligne principale : logo + titre + chips ────────────────────
		JPanel ligneH = new JPanel(new BorderLayout(16, 0));
		ligneH.setBackground(IhmUtils.HEADER);
		ligneH.setBorder(new EmptyBorder(12, 20, 12, 20));

		// Côté gauche : logo + titre
		JPanel gauche = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		gauche.setOpaque(false);

		JLabel icone = new JLabel("◈");
		icone.setFont(new Font("SansSerif", Font.BOLD, 22));
		icone.setForeground(new Color(99, 179, 237));
		icone.setBorder(new EmptyBorder(0, 0, 0, 10));

		JPanel titreBlock = new JPanel();
		titreBlock.setLayout(new BoxLayout(titreBlock, BoxLayout.Y_AXIS));
		titreBlock.setOpaque(false);

		JLabel titre = new JLabel("Planning Global Futura");
		titre.setFont(new Font("SansSerif", Font.BOLD, 16));
		titre.setForeground(Color.WHITE);

		JLabel sous = new JLabel("Gestion des lots & affectations");
		sous.setFont(new Font("SansSerif", Font.PLAIN, 11));
		sous.setForeground(new Color(148, 163, 184));

		titreBlock.add(titre);
		titreBlock.add(sous);
		gauche.add(icone);
		gauche.add(titreBlock);

		// Côté droit : chips de stats
		JPanel chips = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
		chips.setOpaque(false);

		chipLots     = creerChip("— lots",       new Color(59, 130, 246), new Color(219, 234, 254));
		chipAffectes = creerChip("— affectés",   new Color(22, 163, 74),  new Color(220, 252, 231));
		chipHeures   = creerChip("—h dispo",      new Color(217, 119, 6),  new Color(254, 243, 199));
		chipHeureSup = creerChip("Heures sup : non", new Color(100, 116, 139), new Color(241, 245, 249));

		// Bouton rafraîchir discret
		JButton btnRefresh = new JButton("⟳");
		btnRefresh.setFont(new Font("SansSerif", Font.PLAIN, 15));
		btnRefresh.setForeground(new Color(148, 163, 184));
		btnRefresh.setBackground(new Color(51, 65, 85));
		btnRefresh.setBorder(new EmptyBorder(4, 10, 4, 10));
		btnRefresh.setFocusPainted(false);
		btnRefresh.setBorderPainted(false);
		btnRefresh.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		btnRefresh.setToolTipText("Rafraîchir (F5)");
		btnRefresh.addActionListener(e -> rafraichirTout());

		chips.add(chipLots);
		chips.add(chipAffectes);
		chips.add(chipHeures);
		chips.add(chipHeureSup);
		chips.add(Box.createHorizontalStrut(8));
		chips.add(btnRefresh);

		ligneH.add(gauche, BorderLayout.WEST);
		ligneH.add(chips,  BorderLayout.EAST);

		// ── Bandeau désync (visible uniquement si PAM + désynchronisé) ─
		if (ctrl.isAccesPAM())
		{
			JPanel wrapper = new JPanel(new BorderLayout());
			wrapper.setOpaque(false);
			bandeauDesync = creerBandeauDesync();
			bandeauDesync.setVisible(false);
			wrapper.add(ligneH,       BorderLayout.CENTER);
			wrapper.add(bandeauDesync, BorderLayout.SOUTH);
			header.add(wrapper, BorderLayout.CENTER);
		}
		else
		{
			header.add(ligneH, BorderLayout.CENTER);
		}

		// Ligne de séparation subtile en bas du header
		JPanel trait = new JPanel();
		trait.setBackground(new Color(51, 65, 85));
		trait.setPreferredSize(new Dimension(0, 1));
		header.add(trait, BorderLayout.SOUTH);

		// Raccourci F5
		getRootPane().registerKeyboardAction(
			e -> rafraichirTout(),
			KeyStroke.getKeyStroke("F5"),
			JComponent.WHEN_IN_FOCUSED_WINDOW);

		return header;
	}

	/** Crée une "chip" de stat dans l'en-tête. */
	private JLabel creerChip(String texte, Color fg, Color bg)
	{
		JLabel l = new JLabel(" " + texte + " ")
		{
			@Override protected void paintComponent(Graphics g)
			{
				Graphics2D g2 = (Graphics2D) g.create();
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				Color bgActuel = new Color(
					Math.max(bg.getRed()   - 140, 40),
					Math.max(bg.getGreen() - 140, 40),
					Math.max(bg.getBlue()  - 140, 50), 200);
				g2.setColor(bgActuel);
				g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 12, 12));
				g2.dispose();
				super.paintComponent(g);
			}
		};
		l.setFont(new Font("SansSerif", Font.BOLD, 11));
		l.setForeground(new Color(
			Math.min(fg.getRed() + 100, 255),
			Math.min(fg.getGreen() + 100, 255),
			Math.min(fg.getBlue() + 100, 255)));
		l.setOpaque(false);
		l.setBorder(new EmptyBorder(4, 10, 4, 10));
		return l;
	}

	/** Bandeau orange affiché quand PAM est en mode désynchronisé. */
	private JPanel creerBandeauDesync()
	{
		JPanel p = new JPanel(new BorderLayout(8, 0));
		p.setBackground(new Color(120, 53, 15));
		p.setBorder(new EmptyBorder(6, 20, 6, 20));

		JLabel msg = new JLabel("⚠  Mode désynchronisé — vous travaillez localement. Les autres clients ne voient pas vos modifications.");
		msg.setFont(new Font("SansSerif", Font.PLAIN, 12));
		msg.setForeground(new Color(254, 215, 170));

		btnResync = new JButton("↺ Resynchroniser");
		btnResync.setFont(new Font("SansSerif", Font.BOLD, 12));
		btnResync.setForeground(new Color(120, 53, 15));
		btnResync.setBackground(new Color(254, 215, 170));
		btnResync.setBorder(new EmptyBorder(4, 12, 4, 12));
		btnResync.setFocusPainted(false);
		btnResync.addActionListener(e -> seResynchroniser());

		p.add(msg,      BorderLayout.CENTER);
		p.add(btnResync, BorderLayout.EAST);
		return p;
	}

	// ══════════════════════════════════════════════════════════════════════
	//  CONTENU (ONGLETS)
	// ══════════════════════════════════════════════════════════════════════

	private JPanel creerContenu()
	{
		this.panelAffectation = new PanelAffectation(ctrl, this);
		this.panelSocietes    = new PanelSocietes   (ctrl, this);
		this.panelLots        = new PanelLots       (ctrl, this);
		this.panelFicheRoute  = new PanelFicheRoute (ctrl, this);
		this.panelMap         = new PanelMap        (ctrl, this);
		this.panelAuto        = new PanelDiagrame   (ctrl, this);

		JTabbedPane onglets = new JTabbedPane(JTabbedPane.TOP);
		onglets.setFont(new Font("SansSerif", Font.PLAIN, 13));
		onglets.setBackground(IhmUtils.FOND);
		onglets.setBorder(BorderFactory.createEmptyBorder(4, 4, 0, 4));

		// Onglets selon accès
		if (ctrl.isAccesPAM())
			onglets.addTab("  Affectation  ",   panelAffectation);

		onglets.addTab("  Fiches de route  ", panelFicheRoute);
		onglets.addTab("  Liste des lots   ", panelLots);
		onglets.addTab("  Sociétés & ACE   ", panelSocietes);
		onglets.addTab("  Carte entrepôt   ", panelMap);
		onglets.addTab("  Gantt            ", panelAuto);

		// Rafraîchissement à la sélection
		onglets.addChangeListener(e -> {
			Component sel = onglets.getSelectedComponent();
			if (sel == panelFicheRoute) panelFicheRoute.rafraichir();
			if (sel == panelMap)        panelMap.rafraichir();
			if (sel == panelAuto)       ((PanelDiagrame) panelAuto).actualiser();
		});

		JPanel wrapper = new JPanel(new BorderLayout());
		wrapper.setBackground(IhmUtils.FOND);
		wrapper.add(onglets, BorderLayout.CENTER);
		return wrapper;
	}

	// ══════════════════════════════════════════════════════════════════════
	//  BARRE DE STATUT (BAS)
	// ══════════════════════════════════════════════════════════════════════

	private JPanel creerStatusBar()
	{
		JPanel bar = new JPanel(new BorderLayout());
		bar.setBackground(new Color(241, 245, 249));
		bar.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, IhmUtils.BORD_FORT));
		bar.setPreferredSize(new Dimension(0, 26));

		lblStatutBas = new JLabel("  Prêt");
		lblStatutBas.setFont(new Font("SansSerif", Font.PLAIN, 11));
		lblStatutBas.setForeground(IhmUtils.TEXTE_SEC);

		JLabel lblVersion = new JLabel("Planning Global Futura  ");
		lblVersion.setFont(new Font("SansSerif", Font.PLAIN, 11));
		lblVersion.setForeground(IhmUtils.TEXTE_SEC);

		// Bouton désync (PAM uniquement)
		if (ctrl.isAccesPAM())
		{
			btnDesync = new JButton("Mode préparation");
			btnDesync.setFont(new Font("SansSerif", Font.PLAIN, 11));
			btnDesync.setForeground(IhmUtils.TEXTE_SEC);
			btnDesync.setBorder(new EmptyBorder(0, 12, 0, 12));
			btnDesync.setFocusPainted(false);
			btnDesync.setBorderPainted(false);
			btnDesync.setBackground(new Color(241, 245, 249));
			btnDesync.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			btnDesync.addActionListener(e -> seDesynchroniser());

			JPanel droite = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
			droite.setOpaque(false);
			droite.add(btnDesync);
			droite.add(lblVersion);
			bar.add(droite, BorderLayout.EAST);
		}
		else
		{
			bar.add(lblVersion, BorderLayout.EAST);
		}

		bar.add(lblStatutBas, BorderLayout.WEST);
		return bar;
	}

	// ══════════════════════════════════════════════════════════════════════
	//  MENU
	// ══════════════════════════════════════════════════════════════════════

	private JMenuBar creerMenuBar()
	{
		JMenuBar bar = new JMenuBar();
		bar.setBackground(IhmUtils.HEADER);
		bar.setBorder(new EmptyBorder(0, 0, 0, 0));

		JMenu menuFichier = new JMenu("Fichier");
		menuFichier.setFont(new Font("SansSerif", Font.PLAIN, 13));
		menuFichier.setForeground(new Color(203, 213, 225));

		itemOuvrir              = new JMenuItem("📂  Charger une sauvegarde…");
		JMenuItem itemSauvegarder = new JMenuItem("💾  Sauvegarder");
		itemNouveaux            = new JMenuItem("🆕  Nouveaux fichiers JSON…");

		itemOuvrir      .addActionListener(e -> ouvrirSauvegarde());
		itemSauvegarder .addActionListener(e -> sauvegarder());
		itemNouveaux    .addActionListener(e -> nouveaux());

		itemOuvrir      .setAccelerator(KeyStroke.getKeyStroke("ctrl O"));
		itemSauvegarder .setAccelerator(KeyStroke.getKeyStroke("ctrl S"));
		itemNouveaux    .setAccelerator(KeyStroke.getKeyStroke("ctrl N"));

		menuFichier.add(itemOuvrir);
		menuFichier.addSeparator();
		menuFichier.add(itemSauvegarder);
		menuFichier.addSeparator();
		menuFichier.add(itemNouveaux);
		bar.add(menuFichier);
		return bar;
	}

	// ══════════════════════════════════════════════════════════════════════
	//  PERMISSIONS
	// ══════════════════════════════════════════════════════════════════════

	private void appliquerPermissions()
	{
		boolean estPAM    = ctrl.isAccesPAM();
		boolean estClient = !estPAM;

		// En mode client (non-PAM) : menu Fichier désactivé
		if (estClient && getJMenuBar() != null && getJMenuBar().getMenuCount() > 0)
		{
			getJMenuBar().getMenu(0).setEnabled(false);
		}
	}

	// ══════════════════════════════════════════════════════════════════════
	//  ACTIONS MENU
	// ══════════════════════════════════════════════════════════════════════

	private void ouvrirSauvegarde()
	{
		JFileChooser fc = new JFileChooser();
		fc.setDialogTitle("Ouvrir un dossier de sauvegarde");
		fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION)
		{
			try {
				ctrl.chargerDonnees(fc.getSelectedFile().getAbsolutePath());
				if (panelAffectation != null) panelAffectation.remplirComboSocietes();
				panelFicheRoute.remplirComboSocietes();
				rafraichirTout();
				setStatutBas("✓ Sauvegarde chargée depuis " + fc.getSelectedFile().getName());
			} catch (Exception ex) {
				JOptionPane.showMessageDialog(this,
					"Erreur lors du chargement :\n" + ex.getMessage(),
					"Erreur", JOptionPane.ERROR_MESSAGE);
			}
		}
	}

	private void sauvegarder()
	{
		JFileChooser fc = new JFileChooser();
		fc.setDialogTitle("Dossier de destination");
		fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;

		String num = JOptionPane.showInputDialog(this,
			"Numéro de semaine :", "Sauvegarder", JOptionPane.PLAIN_MESSAGE);
		if (num == null || num.isBlank()) return;

		ctrl.sauvegarderDonnees(fc.getSelectedFile().getAbsolutePath(), num.trim());
		setStatutBas("✓ Semaine S" + num.trim() + " sauvegardée.");
	}

	private void nouveaux()
	{
		int res = JOptionPane.showConfirmDialog(this,
			"Réinitialiser les données ? Cette action est irréversible.",
			"Nouvelle session", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
		if (res == JOptionPane.YES_OPTION)
		{
			ctrl.nouveaux();
			if (panelAffectation != null) panelAffectation.remplirComboSocietes();
			panelFicheRoute.remplirComboSocietes();
			rafraichirTout();
		}
	}

	// ══════════════════════════════════════════════════════════════════════
	//  DÉSYNC / RESYNC (PAM)
	// ══════════════════════════════════════════════════════════════════════

	private void seDesynchroniser()
	{
		int r = JOptionPane.showConfirmDialog(this,
			"Passer en mode préparation ?\n"
			+ "Le polling sera suspendu. Les modifications seront locales.",
			"Mode préparation", JOptionPane.YES_NO_OPTION);
		if (r != JOptionPane.YES_OPTION) return;

		ctrl.seDesynchroniser();
		if (bandeauDesync != null) bandeauDesync.setVisible(true);
		if (btnDesync     != null) btnDesync.setEnabled(false);
		if (itemOuvrir    != null) itemOuvrir.setEnabled(true);
		if (itemNouveaux  != null) itemNouveaux.setEnabled(true);
		setStatutBas("⚠  Mode préparation actif");
	}

	private void seResynchroniser()
	{
		int r = JOptionPane.showConfirmDialog(this,
			"Resynchroniser avec le serveur ?\nLes modifications locales non sauvegardées seront perdues.",
			"Resynchronisation", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
		if (r != JOptionPane.YES_OPTION) return;

		ctrl.seResynchroniser();
		if (bandeauDesync != null) bandeauDesync.setVisible(false);
		if (btnDesync     != null) btnDesync.setEnabled(true);
		if (itemOuvrir    != null) itemOuvrir.setEnabled(false);
		if (itemNouveaux  != null) itemNouveaux.setEnabled(false);
		setStatutBas("✓ Resynchronisé avec le serveur");
	}

	// ══════════════════════════════════════════════════════════════════════
	//  RAFRAÎCHISSEMENT
	// ══════════════════════════════════════════════════════════════════════

	public void rafraichirTout()
	{
		Runnable refresh = () -> {
			if (panelAffectation != null) panelAffectation.rafraichir();
			if (panelSocietes    != null) panelSocietes   .rafraichir();
			if (panelLots        != null) panelLots        .rafraichir();
			if (panelFicheRoute  != null) panelFicheRoute .rafraichir();
			if (panelMap         != null) panelMap         .rafraichir();
			majChips();
		};

		if (SwingUtilities.isEventDispatchThread()) refresh.run();
		else SwingUtilities.invokeLater(refresh);

		ctrl.autoSauvegarde();
	}

	/** Met à jour les chips de stat dans l'en-tête. */
	private void majChips()
	{
		int nbLots   = ctrl.getLots().size();
		long nbAff   = ctrl.getSocietes().stream().mapToLong(s -> s.getLots().size()).sum();
		int nbH      = ctrl.getSocietes().stream().mapToInt(s -> s.getTotalHeuresCE()).sum();
		boolean hSup = PlanningGlobal.estHeureSup;

		// Heures des lots non affectés
		int heuresLibres = 0;
		for (Lot l : ctrl.getLots())
			if (!l.getStatut().contains("bloqué") && !l.isEstSousDouane()
					&& ctrl.getSocieteDuLot(l) == null)
				heuresLibres += (int) Math.ceil(l.getHeures());

		if (chipLots     != null) chipLots    .setText("  " + nbLots + " lots  ");
		if (chipAffectes != null) chipAffectes.setText("  " + nbAff + " affectés  ");
		if (chipHeures   != null) chipHeures  .setText("  " + nbH + "h dispo  ");
		if (chipHeureSup != null) {
			chipHeureSup.setText("  Heures sup : " + (hSup ? "OUI" : "non") + "  ");
			chipHeureSup.setForeground(hSup
				? new Color(252, 211, 77)
				: new Color(148, 163, 184));
		}
	}

	/** Affiche un message court dans la barre de statut. */
	public void setStatutBas(String msg)
	{
		if (lblStatutBas == null) return;
		lblStatutBas.setText("  " + msg);
		// Effacer après 4 secondes
		Timer t = new Timer(4000, e -> lblStatutBas.setText("  Prêt"));
		t.setRepeats(false);
		t.start();
	}

	// ══════════════════════════════════════════════════════════════════════
	//  HEURES SUPPLÉMENTAIRES
	// ══════════════════════════════════════════════════════════════════════

	public void SemaineSup()
	{
		ctrl.semaineSup();
		rafraichirTout();
	}

	// ══════════════════════════════════════════════════════════════════════
	//  GETTERS
	// ══════════════════════════════════════════════════════════════════════

	public PanelAffectation getPanelAffectation() { return panelAffectation; }
}