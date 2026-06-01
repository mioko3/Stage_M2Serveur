package app.ihm.serveur;

import app.ServeurHTTP;
import app.metier.PlanningGlobal;
import app.securite.GestionComptes;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.net.*;
import java.util.*;
import javax.swing.*;
import javax.swing.border.*;

/**
 * ══════════════════════════════════════════════════════════════
 *  FenetreServeur — tableau de bord ergonomique
 *
 *  Structure :
 *   ┌─────────────────────────────────────────────────────┐
 *   │  Header : indicateurs (semaine / clients / H.sup)   │
 *   ├──────────┬──────────────────────────────────────────┤
 *   │ Sidebar  │  Contenu principal (cards)               │
 *   │ (menu    │  — Opérations serveur                    │
 *   │  latéral)│  — Semaine suivante                      │
 *   │          │  — Demandes de compte (badge rouge)      │
 *   └──────────┴──────────────────────────────────────────┘
 *   │  Footer : IP + port                                 │
 *   └─────────────────────────────────────────────────────┘
 * ══════════════════════════════════════════════════════════
 */
public class FenetreServeur extends JFrame
{
	private final ServeurHTTP serveur;

	// ── Palette ───────────────────────────────────────────────────────────
	private static final Color C_BG      = new Color(15, 17, 26);
	private static final Color C_SURFACE = new Color(22, 25, 36);
	private static final Color C_CARD    = new Color(30, 34, 50);
	private static final Color C_SIDE    = new Color(18, 21, 32);
	private static final Color C_BORDER  = new Color(45, 50, 70);
	private static final Color C_BLUE    = new Color(64, 128, 230);
	private static final Color C_GREEN   = new Color(38, 168, 90);
	private static final Color C_ORANGE  = new Color(210, 140, 30);
	private static final Color C_RED     = new Color(200, 60, 60);
	private static final Color C_TEXT    = new Color(215, 220, 235);
	private static final Color C_MUTED   = new Color(110, 118, 145);
	private static final Color C_ACCENT  = new Color(100, 160, 255);
	private static final Color C_SIDE_SEL = new Color(40, 46, 68);

	// ── Indicateurs header ────────────────────────────────────────────────
	private JLabel lblSemaine;
	private JLabel lblClients;
	private JLabel lblHeureSup;
	private JLabel lblIP;

	// ── Navigation latérale ───────────────────────────────────────────────
	private static final String[] MENUS = {"⚙  Opérations", "📅  Sem. suivante", "👤  Demandes"};
	private JButton[]  btnMenu   = new JButton[MENUS.length];
	private JPanel     panelContent;
	private CardLayout cardLayout;
	private int        menuActif = 0;

	// ── Panels ────────────────────────────────────────────────────────────
	private JPanel              panelDemandes;
	private PanelSemaineSuivante panelSemSuiv;

	// ── Badge demandes ────────────────────────────────────────────────────
	private JLabel lblBadgeSidebar;
	private JLabel lblBadgeDemandesTot;

	// ══════════════════════════════════════════════════════════════════════
	//  CONSTRUCTEUR
	// ══════════════════════════════════════════════════════════════════════

	public FenetreServeur(ServeurHTTP serveur)
	{
		this.serveur = serveur;

		setTitle("Serveur — Planning Global Futura");
		setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
		addWindowListener(new WindowAdapter() {
			@Override public void windowClosing(WindowEvent e) { confirmerFermeture(); }
		});

		setSize(960, 680);
		setMinimumSize(new Dimension(780, 520));
		setLocationRelativeTo(null);
		getContentPane().setBackground(C_BG);
		setLayout(new BorderLayout());

		add(buildHeader(),  BorderLayout.NORTH);
		add(buildBody(),    BorderLayout.CENTER);
		add(buildFooter(),  BorderLayout.SOUTH);

		// Rafraîchissement toutes les 2s
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
			new EmptyBorder(14, 20, 14, 20)));

		// Logo + titre
		JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
		left.setOpaque(false);
		JLabel logo = new JLabel("▦");
		logo.setFont(new Font("SansSerif", Font.BOLD, 24));
		logo.setForeground(C_ACCENT);
		JPanel titres = new JPanel();
		titres.setLayout(new BoxLayout(titres, BoxLayout.Y_AXIS));
		titres.setOpaque(false);
		JLabel t1 = new JLabel("Panneau de contrôle");
		t1.setFont(new Font("SansSerif", Font.BOLD, 16));
		t1.setForeground(C_TEXT);
		JLabel t2 = new JLabel("Serveur Planning Global Futura");
		t2.setFont(new Font("SansSerif", Font.PLAIN, 11));
		t2.setForeground(C_MUTED);
		titres.add(t1); titres.add(t2);
		left.add(logo); left.add(titres);

		// Indicateurs à droite
		JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
		right.setOpaque(false);

		JPanel cardSem = buildKpi("Semaine", "—", C_ACCENT);
		lblSemaine = findKpiVal(cardSem);
		right.add(cardSem);

		JPanel cardCli = buildKpi("Clients", "0", C_GREEN);
		lblClients = findKpiVal(cardCli);
		right.add(cardCli);

		JPanel cardHS = buildKpi("Heures sup", "non", C_ORANGE);
		lblHeureSup = findKpiVal(cardHS);
		right.add(cardHS);

		// Indicateur EN LIGNE
		JPanel live = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
		live.setOpaque(false);
		JLabel dot = new JLabel("●");
		dot.setFont(new Font("SansSerif", Font.PLAIN, 9));
		dot.setForeground(C_GREEN);
		JLabel lbl = new JLabel("EN LIGNE");
		lbl.setFont(new Font("SansSerif", Font.BOLD, 10));
		lbl.setForeground(C_GREEN);
		live.add(dot); live.add(lbl);
		right.add(live);

		p.add(left,  BorderLayout.WEST);
		p.add(right, BorderLayout.EAST);
		return p;
	}

	/** Crée une carte KPI (label + valeur) pour le header. */
	private JPanel buildKpi(String label, String val, Color couleur)
	{
		JPanel p = new JPanel();
		p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
		p.setBackground(C_CARD);
		p.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(C_BORDER),
			new EmptyBorder(6, 14, 6, 14)));

		JLabel lbl = new JLabel(label);
		lbl.setFont(new Font("SansSerif", Font.PLAIN, 10));
		lbl.setForeground(C_MUTED);
		lbl.setAlignmentX(CENTER_ALIGNMENT);

		JLabel v = new JLabel(val);
		v.setFont(new Font("SansSerif", Font.BOLD, 16));
		v.setForeground(couleur);
		v.setAlignmentX(CENTER_ALIGNMENT);
		v.setName("kpi-val");

		p.add(lbl); p.add(v);
		return p;
	}

	/** Retrouve le label "kpi-val" dans la carte. */
	private JLabel findKpiVal(JPanel card)
	{
		for (Component c : card.getComponents())
			if (c instanceof JLabel && "kpi-val".equals(c.getName()))
				return (JLabel) c;
		return new JLabel(); // fallback
	}

	// ══════════════════════════════════════════════════════════════════════
	//  BODY = SIDEBAR + CONTENU
	// ══════════════════════════════════════════════════════════════════════

	private JPanel buildBody()
	{
		JPanel body = new JPanel(new BorderLayout());
		body.setBackground(C_BG);
		body.add(buildSidebar(),  BorderLayout.WEST);
		body.add(buildContent(),  BorderLayout.CENTER);
		return body;
	}

	// ── Sidebar ───────────────────────────────────────────────────────────

	private JPanel buildSidebar()
	{
		JPanel side = new JPanel();
		side.setLayout(new BoxLayout(side, BoxLayout.Y_AXIS));
		side.setBackground(C_SIDE);
		side.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, C_BORDER));
		side.setPreferredSize(new Dimension(195, 0));

		side.add(Box.createRigidArea(new Dimension(0, 12)));

		for (int i = 0; i < MENUS.length; i++)
		{
			final int idx = i;
			btnMenu[i] = buildMenuBtn(MENUS[i], i == 0);
			btnMenu[i].addActionListener(e -> allerMenu(idx));

			if (i == 2) // Demandes : badge rouge
			{
				JPanel rowBadge = new JPanel(new BorderLayout());
				rowBadge.setOpaque(false);
				rowBadge.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
				rowBadge.add(btnMenu[i], BorderLayout.CENTER);

				lblBadgeSidebar = new JLabel("0");
				lblBadgeSidebar.setFont(new Font("SansSerif", Font.BOLD, 10));
				lblBadgeSidebar.setForeground(Color.WHITE);
				lblBadgeSidebar.setOpaque(true);
				lblBadgeSidebar.setBackground(C_RED);
				lblBadgeSidebar.setBorder(new EmptyBorder(2, 6, 2, 6));
				lblBadgeSidebar.setVisible(false);
				JPanel badgeWrapper = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 10));
				badgeWrapper.setOpaque(false);
				badgeWrapper.add(lblBadgeSidebar);
				rowBadge.add(badgeWrapper, BorderLayout.EAST);
				side.add(rowBadge);
			}
			else
			{
				side.add(btnMenu[i]);
			}
			side.add(Box.createRigidArea(new Dimension(0, 2)));
		}

		side.add(Box.createGlue());
		return side;
	}

	private JButton buildMenuBtn(String label, boolean actif)
	{
		JButton b = new JButton(label);
		b.setFont(new Font("SansSerif", Font.PLAIN, 13));
		b.setForeground(actif ? C_ACCENT : C_MUTED);
		b.setBackground(actif ? C_SIDE_SEL : C_SIDE);
		b.setOpaque(true);
		b.setBorderPainted(false);
		b.setFocusPainted(false);
		b.setHorizontalAlignment(SwingConstants.LEFT);
		b.setBorder(new EmptyBorder(10, 18, 10, 18));
		b.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
		b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		b.addMouseListener(new MouseAdapter() {
			@Override public void mouseEntered(MouseEvent e)
			{
				if (b.getBackground() != C_SIDE_SEL)
				{ b.setBackground(new Color(32, 36, 52)); b.setForeground(C_TEXT); }
			}
			@Override public void mouseExited(MouseEvent e)
			{
				if (b.getBackground() != C_SIDE_SEL)
				{ b.setBackground(C_SIDE); b.setForeground(C_MUTED); }
			}
		});
		return b;
	}

	private void allerMenu(int idx)
	{
		menuActif = idx;
		for (int i = 0; i < btnMenu.length; i++) {
			btnMenu[i].setBackground(i == idx ? C_SIDE_SEL : C_SIDE);
			btnMenu[i].setForeground(i == idx ? C_ACCENT   : C_MUTED);
		}
		String[] keys = {"operations", "semaine", "demandes"};
		cardLayout.show(panelContent, keys[idx]);
		if (idx == 1 && panelSemSuiv != null)
			panelSemSuiv.chargerEtat();
		if (idx == 2)
			rafraichirDemandes();
	}

	// ── Contenu principal ─────────────────────────────────────────────────

	private JPanel buildContent()
	{
		cardLayout  = new CardLayout();
		panelContent = new JPanel(cardLayout);
		panelContent.setBackground(C_BG);

		panelContent.add(buildPanelOperations(), "operations");

		panelSemSuiv = new PanelSemaineSuivante(serveur);
		panelContent.add(panelSemSuiv, "semaine");

		panelDemandes = buildPanelDemandes();
		panelContent.add(panelDemandes, "demandes");

		return panelContent;
	}

	// ── Panel Opérations ──────────────────────────────────────────────────

	private JScrollPane buildPanelOperations()
	{
		JPanel root = new JPanel();
		root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
		root.setBackground(C_BG);
		root.setBorder(new EmptyBorder(20, 20, 20, 20));

		// Titre section
		root.add(buildSectionTitle("Gestion des données"));
		root.add(Box.createRigidArea(new Dimension(0, 10)));

		// Grille 2 colonnes
		JPanel grille = new JPanel(new GridLayout(1, 2, 14, 0));
		grille.setOpaque(false);
		grille.setMaximumSize(new Dimension(Integer.MAX_VALUE, 120));
		grille.setAlignmentX(Component.LEFT_ALIGNMENT);

		grille.add(buildActionCard("📂", "Charger une semaine",
			"Recharger depuis un dossier S17/, S18/…",
			"Charger…", C_BLUE, e -> chargerSemaine()));

		grille.add(buildActionCard("💾", "Sauvegarder la semaine",
			"Archiver dans app/data/enregistrementparsemaine/",
			"Sauvegarder…", C_GREEN, e -> sauvegarder()));

		root.add(grille);
		root.add(Box.createRigidArea(new Dimension(0, 14)));

		JPanel grille2 = new JPanel(new GridLayout(1, 2, 14, 0));
		grille2.setOpaque(false);
		grille2.setMaximumSize(new Dimension(Integer.MAX_VALUE, 120));
		grille2.setAlignmentX(Component.LEFT_ALIGNMENT);

		grille2.add(buildActionCard("🆕", "Nouvelle semaine (Excel)",
			"Importer un fichier XLSX et réinitialiser les données",
			"Importer…", C_ORANGE, e -> nouvelleSemaine()));

		grille2.add(buildActionCard("⏱", "Heures supplémentaires",
			"Fin de journée à 17h15 au lieu de 16h15",
			"Basculer", C_ORANGE, e -> toggleHeuresSup()));

		root.add(grille2);
		root.add(Box.createGlue());

		JScrollPane scroll = new JScrollPane(root);
		scroll.setBorder(null);
		scroll.getViewport().setBackground(C_BG);
		return scroll;
	}

	/** Carte action individuelle (icône + titre + desc + bouton). */
	private JPanel buildActionCard(String icone, String titre, String desc,
								   String labelBtn, Color couleur, ActionListener action)
	{
		JPanel p = new JPanel(new BorderLayout(12, 0));
		p.setBackground(C_CARD);
		p.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(C_BORDER),
			new EmptyBorder(16, 16, 16, 16)));

		// Gauche : icône + textes
		JPanel left = new JPanel();
		left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
		left.setOpaque(false);

		JLabel ico = new JLabel(icone + " " + titre);
		ico.setFont(new Font("SansSerif", Font.BOLD, 13));
		ico.setForeground(C_TEXT);

		JLabel d = new JLabel("<html><span style='color:#6e7690;font-size:10px'>" + desc + "</span></html>");
		d.setFont(new Font("SansSerif", Font.PLAIN, 11));

		left.add(ico);
		left.add(Box.createRigidArea(new Dimension(0, 4)));
		left.add(d);

		// Droite : bouton
		JButton btn = new JButton(labelBtn);
		btn.setBackground(couleur);
		btn.setForeground(Color.WHITE);
		btn.setFocusPainted(false);
		btn.setFont(new Font("SansSerif", Font.BOLD, 12));
		btn.setBorder(new EmptyBorder(8, 14, 8, 14));
		btn.addActionListener(action);

		p.add(left, BorderLayout.CENTER);
		p.add(btn,  BorderLayout.EAST);
		return p;
	}

	// ── Panel Demandes ────────────────────────────────────────────────────

	private JPanel buildPanelDemandes()
	{
		JPanel root = new JPanel(new BorderLayout());
		root.setBackground(C_BG);
		root.setBorder(new EmptyBorder(20, 20, 20, 20));

		// Titre + badge total
		JPanel titreRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
		titreRow.setOpaque(false);

		JLabel titreLbl = new JLabel("Demandes de compte en attente");
		titreLbl.setFont(new Font("SansSerif", Font.BOLD, 16));
		titreLbl.setForeground(C_TEXT);

		lblBadgeDemandesTot = new JLabel("0");
		lblBadgeDemandesTot.setFont(new Font("SansSerif", Font.BOLD, 11));
		lblBadgeDemandesTot.setForeground(Color.WHITE);
		lblBadgeDemandesTot.setOpaque(true);
		lblBadgeDemandesTot.setBackground(C_RED);
		lblBadgeDemandesTot.setBorder(new EmptyBorder(3, 8, 3, 8));
		lblBadgeDemandesTot.setVisible(false);

		titreRow.add(titreLbl);
		titreRow.add(lblBadgeDemandesTot);

		JLabel descLbl = new JLabel(
			"<html><span style='color:#6e7690'>Un administrateur doit approuver ou refuser chaque demande."
			+ " Le compte est actif immédiatement après approbation.</span></html>");
		descLbl.setFont(new Font("SansSerif", Font.PLAIN, 12));

		JPanel top = new JPanel();
		top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
		top.setOpaque(false);
		top.add(titreRow);
		top.add(Box.createRigidArea(new Dimension(0, 6)));
		top.add(descLbl);
		top.add(Box.createRigidArea(new Dimension(0, 16)));
		root.add(top, BorderLayout.NORTH);

		// Zone scrollable des demandes
		JPanel zone = new JPanel();
		zone.setName("zone-demandes");
		zone.setLayout(new BoxLayout(zone, BoxLayout.Y_AXIS));
		zone.setBackground(C_BG);

		JScrollPane scroll = new JScrollPane(zone);
		scroll.setBorder(null);
		scroll.getViewport().setBackground(C_BG);
		root.add(scroll, BorderLayout.CENTER);

		return root;
	}

	private void rafraichirDemandes()
	{
		if (panelDemandes == null) return;

		// Retrouver la zone scrollable
		JScrollPane scroll = null;
		for (Component c : panelDemandes.getComponents())
			if (c instanceof JScrollPane) { scroll = (JScrollPane) c; break; }
		if (scroll == null) return;

		JPanel zone = (JPanel) scroll.getViewport().getView();
		zone.removeAll();

		java.util.List<GestionComptes.DemandeCompte> demandes =
			GestionComptes.getInstance().getDemandesEnAttente();
		int nb = demandes.size();

		// Badge sidebar
		if (lblBadgeSidebar != null) {
			lblBadgeSidebar.setText(String.valueOf(nb));
			lblBadgeSidebar.setVisible(nb > 0);
		}
		// Badge titre
		if (lblBadgeDemandesTot != null) {
			lblBadgeDemandesTot.setText(nb > 0 ? String.valueOf(nb) : "");
			lblBadgeDemandesTot.setVisible(nb > 0);
		}
		// Texte menu sidebar
		btnMenu[2].setText(nb > 0
			? "👤  Demandes (" + nb + ")"
			: "👤  Demandes");

		if (nb == 0) {
			JPanel vide = new JPanel(new BorderLayout());
			vide.setOpaque(false);
			vide.setBorder(new EmptyBorder(30, 0, 0, 0));
			JLabel lbl = new JLabel("✓ Aucune demande en attente.");
			lbl.setFont(new Font("SansSerif", Font.PLAIN, 14));
			lbl.setForeground(C_MUTED);
			lbl.setHorizontalAlignment(SwingConstants.CENTER);
			vide.add(lbl, BorderLayout.CENTER);
			zone.add(vide);
		} else {
			for (GestionComptes.DemandeCompte d : demandes) {
				zone.add(buildLigneDemande(d));
				zone.add(Box.createRigidArea(new Dimension(0, 8)));
			}
		}

		zone.revalidate();
		zone.repaint();
	}

	private JPanel buildLigneDemande(GestionComptes.DemandeCompte dem)
	{
		JPanel p = new JPanel(new BorderLayout(12, 0));
		p.setBackground(C_CARD);
		p.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(C_BORDER),
			new EmptyBorder(12, 16, 12, 16)));
		p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 66));
		p.setAlignmentX(Component.LEFT_ALIGNMENT);

		// Identifiant + date
		JPanel info = new JPanel();
		info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));
		info.setOpaque(false);
		JLabel id = new JLabel(dem.identifiant);
		id.setFont(new Font("SansSerif", Font.BOLD, 14));
		id.setForeground(C_TEXT);
		JLabel date = new JLabel(dem.date);
		date.setFont(new Font("SansSerif", Font.PLAIN, 11));
		date.setForeground(C_MUTED);
		info.add(id);
		info.add(date);
		p.add(info, BorderLayout.CENTER);

		// Boutons
		JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
		btns.setOpaque(false);

		JButton btnOk = new JButton("✓ Approuver");
		btnOk.setBackground(C_GREEN);
		btnOk.setForeground(Color.WHITE);
		btnOk.setFocusPainted(false);
		btnOk.setFont(new Font("SansSerif", Font.BOLD, 12));
		btnOk.setBorder(new EmptyBorder(7, 14, 7, 14));
		btnOk.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		btnOk.addActionListener(e -> {
			GestionComptes.getInstance().approuver(dem.identifiant);
			JOptionPane.showMessageDialog(this,
				dem.identifiant + " peut maintenant se connecter.",
				"Compte approuvé ✓", JOptionPane.INFORMATION_MESSAGE);
			rafraichirDemandes();
		});

		JButton btnKo = new JButton("✕ Refuser");
		btnKo.setBackground(new Color(55, 28, 28));
		btnKo.setForeground(new Color(230, 100, 100));
		btnKo.setFocusPainted(false);
		btnKo.setFont(new Font("SansSerif", Font.BOLD, 12));
		btnKo.setBorder(new EmptyBorder(7, 14, 7, 14));
		btnKo.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		btnKo.addActionListener(e -> {
			int r = JOptionPane.showConfirmDialog(this,
				"Refuser la demande de \"" + dem.identifiant + "\" ?",
				"Confirmer", JOptionPane.YES_NO_OPTION);
			if (r != JOptionPane.YES_OPTION) return;
			GestionComptes.getInstance().refuser(dem.identifiant);
			rafraichirDemandes();
		});

		btns.add(btnOk);
		btns.add(btnKo);
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
			new EmptyBorder(8, 20, 8, 20)));

		JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
		left.setOpaque(false);
		JLabel dot = new JLabel("●");
		dot.setFont(new Font("SansSerif", Font.PLAIN, 9));
		dot.setForeground(C_GREEN);
		JLabel srv = new JLabel("Serveur actif — Port 8082");
		srv.setFont(new Font("SansSerif", Font.PLAIN, 11));
		srv.setForeground(C_MUTED);
		left.add(dot);
		left.add(srv);

		lblIP = new JLabel("IP : " + detecterIP());
		lblIP.setFont(new Font("SansSerif", Font.BOLD, 11));
		lblIP.setForeground(C_ACCENT);

		p.add(left,  BorderLayout.WEST);
		p.add(lblIP, BorderLayout.EAST);
		return p;
	}

	// ══════════════════════════════════════════════════════════════════════
	//  ACTIONS OPÉRATIONS
	// ══════════════════════════════════════════════════════════════════════

	private void chargerSemaine()
	{
		JFileChooser fc = new JFileChooser();
		fc.setDialogTitle("Sélectionner le dossier de sauvegarde (ex: S17)");
		fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		File def = new File(app.CheminApp.resoudre("app/data/enregistrementparsemaine"));
		if (def.exists()) fc.setCurrentDirectory(def);
		if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;

		int confirm = JOptionPane.showConfirmDialog(this,
			"Charger la semaine depuis :\n" + fc.getSelectedFile().getAbsolutePath()
				+ "\n\nTous les clients basculeront sur ces données.",
			"Confirmer le chargement", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
		if (confirm != JOptionPane.YES_OPTION) return;

		try {
			serveur.chargerSemaine(fc.getSelectedFile().getAbsolutePath());
			refresh();
			JOptionPane.showMessageDialog(this,
				"Semaine chargée. Les clients se synchroniseront dans les 3 secondes.",
				"Chargement OK ✓", JOptionPane.INFORMATION_MESSAGE);
		} catch (Exception ex) {
			JOptionPane.showMessageDialog(this,
				"Erreur :\n" + ex.getMessage(), "Erreur", JOptionPane.ERROR_MESSAGE);
		}
	}

	private void nouvelleSemaine()
	{
		int ok = JOptionPane.showConfirmDialog(this,
			"Charger un nouveau fichier Excel ?\n"
			+ "Les données actuelles restent disponibles jusqu'à la prochaine sauvegarde.",
			"Nouvelle semaine", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
		if (ok != JOptionPane.YES_OPTION) return;
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
		File def = new File(app.CheminApp.resoudre("app/data/enregistrementparsemaine"));
		if (def.exists()) fc.setCurrentDirectory(def);
		if (fc.showDialog(this, "Sauvegarder ici") != JFileChooser.APPROVE_OPTION) return;

		String num = JOptionPane.showInputDialog(this,
			"Numéro de semaine :", "Sauvegarde", JOptionPane.PLAIN_MESSAGE);
		if (num == null || num.isBlank()) return;

		try {
			serveur.sauvegarderSemaine(fc.getSelectedFile().getAbsolutePath(), num.trim());
			JOptionPane.showMessageDialog(this,
				"Sauvegarde effectuée dans S" + num.trim() + " ✓",
				"Sauvegarde OK", JOptionPane.INFORMATION_MESSAGE);
		} catch (Exception ex) {
			JOptionPane.showMessageDialog(this,
				"Erreur :\n" + ex.getMessage(), "Erreur", JOptionPane.ERROR_MESSAGE);
		}
	}

	private void toggleHeuresSup()
	{
		serveur.toggleHeuresSup();
		boolean etat = PlanningGlobal.estHeureSup;
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
		boolean hs = PlanningGlobal.estHeureSup;
		lblHeureSup.setText(hs ? "OUI" : "non");
		lblHeureSup.setForeground(hs ? C_ORANGE : C_MUTED);

		// Badge demandes (en arrière-plan)
		SwingUtilities.invokeLater(() -> {
			int nbDem = GestionComptes.getInstance().getDemandesEnAttente().size();
			if (lblBadgeSidebar != null) {
				lblBadgeSidebar.setText(String.valueOf(nbDem));
				lblBadgeSidebar.setVisible(nbDem > 0);
			}
			if (lblBadgeDemandesTot != null) {
				lblBadgeDemandesTot.setText(nbDem > 0 ? String.valueOf(nbDem) : "");
				lblBadgeDemandesTot.setVisible(nbDem > 0);
			}
			if (btnMenu[2] != null)
				btnMenu[2].setText(nbDem > 0
					? "👤  Demandes (" + nbDem + ")"
					: "👤  Demandes");
		});
	}

	// ══════════════════════════════════════════════════════════════════════
	//  HELPERS
	// ══════════════════════════════════════════════════════════════════════

	private JLabel buildSectionTitle(String t)
	{
		JLabel l = new JLabel(t);
		l.setFont(new Font("SansSerif", Font.BOLD, 12));
		l.setForeground(C_MUTED);
		l.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 0, 1, 0, C_BORDER),
			new EmptyBorder(0, 0, 8, 0)));
		l.setAlignmentX(Component.LEFT_ALIGNMENT);
		l.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
		return l;
	}

	private void confirmerFermeture()
	{
		int r = JOptionPane.showConfirmDialog(this,
			"Arrêter le serveur ?\nTous les clients seront déconnectés.",
			"Quitter", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
		if (r == JOptionPane.YES_OPTION) System.exit(0);
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