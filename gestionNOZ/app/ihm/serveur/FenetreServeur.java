package app.ihm.serveur;

import app.ServeurHTTP;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.io.File;
import java.net.InetAddress;

/**
 * ══════════════════════════════════════════════════════════════
 *  FenetreServeur — IHM de contrôle côté SERVEUR uniquement.
 *
 *  Permet au responsable serveur de :
 *   • Charger une semaine sauvegardée  → tous les clients basculent
 *   • Créer une nouvelle semaine       → réinitialisation serveur
 *   • Sauvegarder la semaine courante
 *
 *  Les clients (ControleurClient) ont ces actions BLOQUÉES :
 *  ils ne peuvent ni charger ni créer de semaine eux-mêmes.
 * ══════════════════════════════════════════════════════════════
 */
public class FenetreServeur extends JFrame
{
	private final ServeurHTTP serveur;

	// Indicateur semaine active
	private JLabel lblSemaineActive;
	private JLabel lblNbClients;

	// ── Couleurs ─────────────────────────────────────────────
	private static final Color FOND        = new Color(28, 28, 40);
	private static final Color FOND_PANEL  = new Color(38, 38, 55);
	private static final Color BLEU        = new Color(60, 120, 220);
	private static final Color VERT        = new Color(40, 160, 80);
	private static final Color ROUGE       = new Color(200, 60, 60);
	private static final Color ORANGE      = new Color(200, 130, 30);
	private static final Color TEXTE       = new Color(220, 220, 235);
	private static final Color TEXTE_GRIS  = new Color(140, 140, 160);

	// ── Constructeur ─────────────────────────────────────────
	public FenetreServeur(ServeurHTTP serveur)
	{
		this.serveur = serveur;

		setTitle("🖥  Serveur gestionNOZ — Panneau de contrôle");
		setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
		addWindowListener(new java.awt.event.WindowAdapter()
		{
			@Override public void windowClosing(java.awt.event.WindowEvent e)
			{
				int r = JOptionPane.showConfirmDialog(FenetreServeur.this,
					"Arrêter le serveur ? Tous les clients seront déconnectés.",
					"Confirmation", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
				if (r == JOptionPane.YES_OPTION) System.exit(0);
			}
		});

		setSize(650, 540);
		setLocationRelativeTo(null);
		setResizable(false);
		getContentPane().setBackground(FOND);
		setLayout(new BorderLayout(0, 0));

		add(construireEntete(),    BorderLayout.NORTH);
		add(construirePanneaux(),  BorderLayout.CENTER);
		add(construireStatut(),    BorderLayout.SOUTH);

		// Rafraîchir le statut toutes les 2 secondes
		Timer timer = new Timer(2000, e -> mettreAJourStatut());
		timer.setRepeats(true);
		timer.start();

		setVisible(true);
		mettreAJourStatut();
	}

	// ── En-tête ───────────────────────────────────────────────
	private JPanel construireEntete()
	{
		JPanel p = new JPanel(new BorderLayout());
		p.setBackground(new Color(18, 18, 30));
		p.setBorder(new EmptyBorder(14, 20, 14, 20));

		JLabel titre = new JLabel("Panneau de contrôle — Serveur");
		titre.setFont(new Font("SansSerif", Font.BOLD, 17));
		titre.setForeground(Color.WHITE);

		lblSemaineActive = new JLabel("Semaine active : —");
		lblSemaineActive.setFont(new Font("SansSerif", Font.PLAIN, 12));
		lblSemaineActive.setForeground(new Color(100, 200, 255));

		p.add(titre,            BorderLayout.WEST);
		p.add(lblSemaineActive, BorderLayout.EAST);
		return p;
	}

	// ── Corps principal ───────────────────────────────────────
	private JPanel construirePanneaux()
	{
		JPanel corps = new JPanel();
		corps.setBackground(FOND);
		corps.setLayout(new BoxLayout(corps, BoxLayout.Y_AXIS));
		corps.setBorder(new EmptyBorder(16, 20, 8, 20));

		corps.add(construirePanelSemaine());
		corps.add(Box.createVerticalStrut(14));
		corps.add(construirePanelSauvegarde());
		corps.add(Box.createVerticalStrut(14));
		corps.add(construirePanelInfo());

		return corps;
	}

	// ── Panel : Gestion de la semaine ─────────────────────────
	private JPanel construirePanelSemaine()
	{
		JPanel p = panelBordure("📅  Semaine de travail des clients");

		// Charger une semaine existante
		JButton btnCharger = bouton("📂  Charger une semaine sauvegardée…", BLEU);
		btnCharger.addActionListener(e -> chargerSemaine());

		// Créer / importer une nouvelle semaine
		JButton btnNouveaux = bouton("🆕  Nouvelle semaine (import Excel)…", ORANGE);
		btnNouveaux.addActionListener(e -> nouvelleSemaine());

		JLabel infoCharger = infoLabel(
			"Tous les clients basculeront automatiquement sur les données chargées.");
		JLabel infoNouveaux = infoLabel(
			"Réinitialise le serveur avec un nouveau fichier Excel. Les clients se synchroniseront.");

		p.add(btnCharger);
		p.add(Box.createVerticalStrut(3));
		p.add(infoCharger);
		p.add(Box.createVerticalStrut(10));
		p.add(btnNouveaux);
		p.add(Box.createVerticalStrut(3));
		p.add(infoNouveaux);

		return p;
	}

	// ── Panel : Sauvegarde ────────────────────────────────────
	private JPanel construirePanelSauvegarde()
	{
		JPanel p = panelBordure("💾  Sauvegarde");

		JButton btnSauvegarder = bouton("💾  Sauvegarder la semaine courante…", VERT);
		btnSauvegarder.addActionListener(e -> sauvegarder());

		JLabel info = infoLabel(
			"Enregistre un snapshot de la semaine dans app/data/enregistrementparsemaine/.");

		p.add(btnSauvegarder);
		p.add(Box.createVerticalStrut(3));
		p.add(info);

		return p;
	}

	// ── Panel : Informations ──────────────────────────────────
	private JPanel construirePanelInfo()
	{
		JPanel p = panelBordure("ℹ️  Informations");
		try
		{
			lblNbClients = new JLabel("Clients connectés : —");
			lblNbClients.setFont(new Font("SansSerif", Font.PLAIN, 12));
			lblNbClients.setForeground(TEXTE);
			lblNbClients.setAlignmentX(Component.LEFT_ALIGNMENT);
			
			InetAddress ip = InetAddress.getLocalHost();
			JLabel lblPort = new JLabel("Port HTTP : "+ ip.getHostAddress() );
			lblPort.setFont(new Font("SansSerif", Font.PLAIN, 12));
			lblPort.setForeground(TEXTE_GRIS);
			lblPort.setAlignmentX(Component.LEFT_ALIGNMENT);

			JLabel lblInfo = infoLabel(
				"Les clients ne peuvent pas changer de semaine ni charger de données eux-mêmes.");

			p.add(lblNbClients);
			p.add(Box.createVerticalStrut(4));
			p.add(lblPort);
			p.add(Box.createVerticalStrut(4));
			p.add(lblInfo);
		}catch(Exception e) {System.err.println("erreur LocalHost");}

		return p;
	}

	// ── Barre de statut ───────────────────────────────────────
	private JPanel construireStatut()
	{
		JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 6));
		p.setBackground(new Color(18, 18, 30));

		JLabel dot = new JLabel("●");
		dot.setFont(new Font("SansSerif", Font.BOLD, 14));
		dot.setForeground(VERT);

		JLabel txt = new JLabel("Serveur en cours d'exécution — Port 8080");
		txt.setFont(new Font("SansSerif", Font.PLAIN, 12));
		txt.setForeground(TEXTE_GRIS);

		p.add(dot);
		p.add(txt);
		return p;
	}

	// ── Actions ───────────────────────────────────────────────

	private void chargerSemaine()
	{
		JFileChooser fc = new JFileChooser();
		fc.setDialogTitle("Sélectionner le dossier de sauvegarde (ex: S17)");
		fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		File def = new File("app/data/enregistrementparsemaine");
		if (def.exists()) fc.setCurrentDirectory(def);

		if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;

		String chemin = fc.getSelectedFile().getAbsolutePath();

		int confirm = JOptionPane.showConfirmDialog(this,
			"Charger la semaine depuis :\n" + chemin
				+ "\n\nTous les clients basculeront sur ces données.",
			"Confirmer le chargement", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
		if (confirm != JOptionPane.YES_OPTION) return;

		try
		{
			serveur.chargerSemaine(chemin);
			mettreAJourStatut();
			JOptionPane.showMessageDialog(this,
				"Semaine chargée. Les clients se synchroniseront dans les 3 secondes.",
				"Chargement OK", JOptionPane.INFORMATION_MESSAGE);
		}
		catch (Exception ex)
		{
			JOptionPane.showMessageDialog(this,
				"Erreur lors du chargement :\n" + ex.getMessage(),
				"Erreur", JOptionPane.ERROR_MESSAGE);
		}
	}

	private void nouvelleSemaine()
	{
		int confirm = JOptionPane.showConfirmDialog(this,
			"Charger un nouveau fichier Excel pour une nouvelle semaine ?\n"
				+ "Les données actuelles resteront en mémoire jusqu'à la sauvegarde.",
			"Nouvelle semaine", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
		if (confirm != JOptionPane.YES_OPTION) return;

		try
		{
			serveur.nouvelleSemaine(this);
			mettreAJourStatut();
		}
		catch (Exception ex)
		{
			JOptionPane.showMessageDialog(this,
				"Erreur :\n" + ex.getMessage(),
				"Erreur", JOptionPane.ERROR_MESSAGE);
		}
	}

	private void sauvegarder()
	{
		JFileChooser fc = new JFileChooser();
		fc.setDialogTitle("Dossier de destination pour la sauvegarde");
		fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		File def = new File("app/data/enregistrementparsemaine");
		if (def.exists()) fc.setCurrentDirectory(def);

		if (fc.showDialog(this, "Sauvegarder ici") != JFileChooser.APPROVE_OPTION) return;

		String numSemaine = JOptionPane.showInputDialog(
			this, "Numéro de semaine :", "Sauvegarde", JOptionPane.PLAIN_MESSAGE);
		if (numSemaine == null || numSemaine.isBlank()) return;

		try
		{
			serveur.sauvegarderSemaine(
				fc.getSelectedFile().getAbsolutePath(), numSemaine.trim());
			JOptionPane.showMessageDialog(this,
				"Sauvegarde effectuée dans S" + numSemaine.trim(),
				"Sauvegarde OK", JOptionPane.INFORMATION_MESSAGE);
		}
		catch (Exception ex)
		{
			JOptionPane.showMessageDialog(this,
				"Erreur :\n" + ex.getMessage(),
				"Erreur", JOptionPane.ERROR_MESSAGE);
		}
	}

	// ── Rafraîchissement statut ───────────────────────────────
	private void mettreAJourStatut()
	{
		String sem = serveur.getSemaineActive();
		lblSemaineActive.setText("Semaine active : " + (sem != null && !sem.isBlank() ? sem : "—"));

		int nb = serveur.getNbClientsConnectes();
		lblNbClients.setText("Clients connectés (polling actif < 10s) : " + nb);
	}

	// ── Helpers UI ────────────────────────────────────────────
	private JPanel panelBordure(String titre)
	{
		JPanel p = new JPanel();
		p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
		p.setBackground(FOND_PANEL);
		p.setAlignmentX(Component.LEFT_ALIGNMENT);
		p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 200));

		TitledBorder border = BorderFactory.createTitledBorder(
			BorderFactory.createLineBorder(new Color(70, 70, 100), 1), titre);
		border.setTitleColor(TEXTE_GRIS);
		border.setTitleFont(new Font("SansSerif", Font.BOLD, 11));
		p.setBorder(BorderFactory.createCompoundBorder(
			border,
			new EmptyBorder(8, 10, 10, 10)));

		return p;
	}

	private JButton bouton(String texte, Color couleur)
	{
		JButton b = new JButton(texte);
		b.setAlignmentX(Component.LEFT_ALIGNMENT);
		b.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
		b.setBackground(couleur);
		b.setForeground(Color.WHITE);
		b.setFont(new Font("SansSerif", Font.BOLD, 13));
		b.setFocusPainted(false);
		b.setCursor(new Cursor(Cursor.HAND_CURSOR));
		b.setBorder(new EmptyBorder(8, 16, 8, 16));
		return b;
	}

	private JLabel infoLabel(String texte)
	{
		JLabel l = new JLabel("  ⓘ " + texte);
		l.setFont(new Font("SansSerif", Font.ITALIC, 11));
		l.setForeground(TEXTE_GRIS);
		l.setAlignmentX(Component.LEFT_ALIGNMENT);
		return l;
	}
}