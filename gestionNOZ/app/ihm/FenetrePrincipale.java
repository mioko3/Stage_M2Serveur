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
import java.awt.FlowLayout;
import java.awt.Font;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
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

	private JLabel lblStatutConnexion; // label visible en permanence
	private JLabel dotConnexion;       // point coloré ● 

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
		this.panelAuto        = new PanelDiagrame   (ctrl, this);

		JTabbedPane onglets = new JTabbedPane();
		onglets.setFont(new Font("SansSerif", Font.PLAIN, 13));

		if (ctrl.isAccesPAM())
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
		p.setBorder(BorderFactory.createEmptyBorder(10, 18, 10, 18));

		JLabel titre = new JLabel("Planning Global Futura — Gestion des lots");
		titre.setForeground(Color.WHITE);
		titre.setFont(new Font("SansSerif", Font.BOLD, 17));

		JButton btnRafraichir = new JButton("⟳");
		btnRafraichir.setFont(new Font("SansSerif", Font.PLAIN, 12));
		btnRafraichir.setBackground(IhmUtils.HEADER);
		btnRafraichir.setForeground(new Color(255, 255, 180));
		btnRafraichir.addActionListener(e -> this.rafraichirTout());

		lblInfo = new JLabel(buildInfo());
		lblInfo.setForeground(new Color(180, 180, 180));
		lblInfo.setFont(new Font("SansSerif", Font.PLAIN, 12));

		// ── Indicateur de connexion ──────────────────────────────────
		lblStatutConnexion = new JLabel("● EN LIGNE");
		lblStatutConnexion.setForeground(new Color(52, 211, 153)); // vert
		lblStatutConnexion.setFont(new Font("SansSerif", Font.BOLD, 12));
		lblStatutConnexion.setBorder(BorderFactory.createEmptyBorder(0, 16, 0, 0));

		JPanel panelDroite = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
		panelDroite.setOpaque(false);
		panelDroite.add(lblInfo);
		panelDroite.add(lblStatutConnexion);

		JPanel panelBtn = new JPanel(new FlowLayout(FlowLayout.LEFT));
		panelBtn.setBackground(IhmUtils.HEADER);
		if (!ctrl.isAccesPAM())
		{
			JButton btnSup = new JButton("Heures Sup");
			btnSup.setFont(new Font("SansSerif", Font.PLAIN, 12));
			btnSup.setBackground(IhmUtils.HEADER);
			btnSup.setForeground(new Color(255, 255, 180));
			btnSup.setEnabled(true);
			btnSup.addActionListener(e -> this.SemaineSup());
			panelBtn.add(btnSup);
		}
		panelBtn.add(btnRafraichir);

		p.add(titre,       BorderLayout.WEST);
		p.add(panelDroite, BorderLayout.EAST);  // ← lblInfo + lblStatutConnexion ensemble
		p.add(panelBtn,    BorderLayout.SOUTH);

		return p;
	}

	// ── À appeler depuis le thread de polling ────────────────────────────
	/** Appelé par ControleurClient.demarrerPolling() quand la connexion change. */
	public void setStatutConnexion(boolean enLigne)
	{
		SwingUtilities.invokeLater(() -> {
			if (lblStatutConnexion == null) return;
			if (enLigne) {
				lblStatutConnexion.setText("● EN LIGNE");
				lblStatutConnexion.setForeground(new Color(52, 211, 153)); // vert
				lblStatutConnexion.setToolTipText("Synchronisé avec le serveur");
			} else {
				lblStatutConnexion.setText("● HORS LIGNE");
				lblStatutConnexion.setForeground(new Color(220, 38, 38));  // rouge
				lblStatutConnexion.setToolTipText("Serveur inaccessible — modifications non sauvegardées");
			}
		});
	}

	private String buildInfo()
	{
		long nbAff = ctrl.getSocietes().stream().mapToLong(s -> s.getLots().size()).sum();
		int  nbH   = ctrl.getSocietes().stream().mapToInt(s -> s.getTotalHeuresCE()).sum();
		String heureSup = PlanningGlobal.estHeureSup ? "oui" : "non";
		String desyncInfo = "";
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
		this.ctrl.autoSauvegarde();
	}

	public void SemaineSup()
	{
		ctrl.semaineSup();
		rafraichirTout();
	}

	public PanelAffectation getPanelAffectation() { return panelAffectation; }
}	