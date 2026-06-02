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
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;

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
	private PanelDiagrame    panelDiagrame;
	private JLabel           lblInfo;


	// ── Constructeur ─────────────────────────────────────────────────────

	public FenetrePrincipale(IControleur ctrl)
	{
		this.ctrl = ctrl;
		setTitle("Planning Global Futura — PAM");
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setSize(1480, 780);
		setExtendedState(JFrame.MAXIMIZED_BOTH);
		setLocationRelativeTo(null);
		setLayout(new BorderLayout());
		getContentPane().setBackground(IhmUtils.FOND);

		add(creerEntete(), BorderLayout.NORTH);

		this.panelAffectation = new PanelAffectation(ctrl, this);
		this.panelSocietes    = new PanelSocietes   (ctrl, this);
		this.panelLots        = new PanelLots       (ctrl, this);
		this.panelFicheRoute  = new PanelFicheRoute (ctrl, this);
		this.panelMap         = new PanelMap        (ctrl, this);
		this.panelDiagrame        = new PanelDiagrame   (ctrl, this);

		JTabbedPane onglets = new JTabbedPane();
		onglets.setFont(new Font("SansSerif", Font.PLAIN, 13));

		if (ctrl.isAccesPAM())
			onglets.addTab("⊕ Affectation", panelAffectation);

		onglets.addTab("📋 Fiches de Route",   panelFicheRoute);
		onglets.addTab("☰ Liste des lots",    panelLots);
		onglets.addTab("🕒 Sociétés & heures", panelSocietes);
		onglets.addTab("🗺 Carte entrepôt",    panelMap);
		onglets.addTab("⚙ DiagrameGantt",     panelDiagrame);
		add(onglets, BorderLayout.CENTER);

		onglets.addChangeListener(e -> {
			if (onglets.getSelectedComponent() == panelFicheRoute)
				panelFicheRoute.rafraichir();
			if (onglets.getSelectedComponent() == panelMap)
				panelMap.rafraichir();
		});

		panelAffectation.remplirComboSocietes();

		rafraichirTout();
		setVisible(true);
	}

	public void nouveaux()
	{
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
		p.setBorder(new EmptyBorder(0, 0, 0, 0));

		// ── Ligne principale ──────────────────────────────────────────────
		JPanel ligne = new JPanel(new BorderLayout(16, 0));
		ligne.setBackground(IhmUtils.HEADER);
		ligne.setBorder(new EmptyBorder(14, 22, 14, 22));

		// Gauche : icône + titres
		JPanel gauche = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		gauche.setOpaque(false);

		JLabel ico = new JLabel("⬡  ");
		ico.setFont(new Font("SansSerif", Font.BOLD, 20));
		ico.setForeground(new Color(96, 165, 250));

		JPanel titreBloc = new JPanel();
		titreBloc.setLayout(new BoxLayout(titreBloc, BoxLayout.Y_AXIS));
		titreBloc.setOpaque(false);

		JLabel titre = new JLabel("Planning Global Futura");
		titre.setFont(new Font(IhmUtils.FONT_NAME, Font.BOLD, 16));
		titre.setForeground(Color.WHITE);

		JLabel sous = new JLabel("Gestion des lots & fiches de route");
		sous.setFont(new Font(IhmUtils.FONT_NAME, Font.PLAIN, 11));
		sous.setForeground(new Color(148, 163, 184));

		titreBloc.add(titre);
		titreBloc.add(sous);
		gauche.add(ico);
		gauche.add(titreBloc);

		// Droite : chips d'info + boutons
		JPanel droite = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
		droite.setOpaque(false);

		lblInfo = new JLabel(buildInfo());
		lblInfo.setFont(new Font(IhmUtils.FONT_NAME, Font.PLAIN, 13));
		lblInfo.setForeground(new Color(148, 163, 184));
		droite.add(lblInfo);

		if (!ctrl.isAccesPAM())
		{
			JButton btnSup = IhmUtils.bouton("⏱ Heures Sup",
				new Color(180, 100, 20), Color.WHITE);
			btnSup.setFont(new Font("SansSerif", Font.BOLD, 20));
			btnSup.addActionListener(e -> SemaineSup());
			droite.add(btnSup);
		}

		JButton btnRafraichir = IhmUtils.boutonCompact("⟳ Rafraîchir",
			IhmUtils.HEADER2, new Color(148, 163, 184));
		btnRafraichir.setFont(new Font("SansSerif", Font.BOLD, 20));
		btnRafraichir.addActionListener(e -> rafraichirTout());
		droite.add(btnRafraichir);

		droite.add(ConnectLive());
		ligne.add(gauche, BorderLayout.WEST);
		ligne.add(droite, BorderLayout.EAST);

		// Trait bas
		JPanel trait = new JPanel();
		trait.setBackground(new Color(51, 65, 85));
		trait.setPreferredSize(new Dimension(1, 1));

		p.add(ligne, BorderLayout.CENTER);
		p.add(trait, BorderLayout.SOUTH);
		return p;
	}

	private String buildInfo()
	{
		long nbAff = ctrl.getSocietes().stream().mapToLong(s -> s.getLots().size()).sum();
		int  nbH   = ctrl.getSocietes().stream().mapToInt(s -> s.getTotalHeuresCE()).sum();
		String heureSup = PlanningGlobal.estHeureSup ? "oui" : "non";
		return ctrl.getLots().size() + " lots  |  " + " Heures total Lot" + getHeureLotTotal()
			+ ctrl.getSocietes().size() + " sociétés  |  "
			+ nbAff + " affectés  |  "
			+ nbH + "h disponibles  |  Heures Sup : " + heureSup;
	}

	private JPanel ConnectLive()
	{
		JPanel live = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
		live.setOpaque(false);
		live.setBorder(new EmptyBorder(0, 8, 0, 0));
		JLabel dot = new JLabel("●");
		dot.setFont(new Font(IhmUtils.FONT_NAME, Font.PLAIN, 9));

		Color liveColor  = ctrl.isPollingActif() ? IhmUtils.GREEN_LIVE : IhmUtils.RED_LIVE;
		String connexion = ctrl.isPollingActif() ? "EN LIGNE" : "HORS LIGNE";
		System.out.println("Polling actif : " + ctrl.isPollingActif());
		System.out.println("Connexion serveur : " + connexion);


		dot.setForeground(liveColor);
		JLabel txtLive = new JLabel(connexion);
		txtLive.setFont(new Font(IhmUtils.FONT_NAME, Font.BOLD, 11));
		txtLive.setForeground(liveColor);
		live.add(dot);
		live.add(txtLive);
		return live;
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
		this.ctrl.autoSauvegarde();
	}

	public void SemaineSup()
	{
		ctrl.semaineSup();
		rafraichirTout();
	}

	public PanelAffectation getPanelAffectation() { return panelAffectation; }
}	