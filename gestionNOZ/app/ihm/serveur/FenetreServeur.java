package app.ihm.serveur;

import app.ServeurHTTP;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;
import java.io.File;

/**
 * ══════════════════════════════════════════════════════════════
 *  FenetreServeur — IHM de contrôle côté SERVEUR uniquement.
 *
 *  Design : tableau de bord industriel sombre, cartes aérées,
 *  indicateurs visuels colorés, hiérarchie claire.
 * ══════════════════════════════════════════════════════════════
 */
public class FenetreServeur extends JFrame
{
	private final ServeurHTTP serveur;

	// Labels dynamiques
	private JLabel lblSemaine;
	private JLabel lblHeureSup;
	private JLabel lblClients;
	private JLabel lblIP;

	// ── Palette ──────────────────────────────────────────────
	private static final Color C_BG         = new Color(15, 17, 26);
	private static final Color C_SURFACE    = new Color(24, 27, 40);
	private static final Color C_CARD       = new Color(30, 34, 50);
	private static final Color C_BORDER     = new Color(50, 55, 78);
	private static final Color C_BLUE       = new Color(64, 128, 230);
	private static final Color C_GREEN      = new Color(38, 168, 90);
	private static final Color C_ORANGE     = new Color(210, 140, 30);
	private static final Color C_RED        = new Color(210, 65, 65);
	private static final Color C_TEXT       = new Color(215, 220, 235);
	private static final Color C_MUTED      = new Color(120, 128, 155);
	private static final Color C_ACCENT     = new Color(100, 160, 255);

	// ── Constructeur ─────────────────────────────────────────
	public FenetreServeur(ServeurHTTP serveur)
	{
		this.serveur = serveur;

		setTitle("Serveur gestionNOZ — Panneau de contrôle");
		setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
		addWindowListener(new java.awt.event.WindowAdapter()
		{
			@Override public void windowClosing(java.awt.event.WindowEvent e)
			{
				int r = JOptionPane.showConfirmDialog(FenetreServeur.this,
					"Arrêter le serveur ?\nTous les clients seront déconnectés.",
					"Confirmation", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
				if (r == JOptionPane.YES_OPTION) System.exit(0);
			}
		});

		setSize(780, 600);
		setMinimumSize(new Dimension(520, 500));
		setLocationRelativeTo(null);
		setResizable(true);
		getContentPane().setBackground(C_BG);
		setLayout(new BorderLayout(0, 0));

		add(buildHeader(),  BorderLayout.NORTH);
		add(buildScroll(),  BorderLayout.CENTER);
		add(buildFooter(),  BorderLayout.SOUTH);

		Timer t = new Timer(2000, e -> refresh());
		t.setRepeats(true);
		t.start();

		setVisible(true);
		refresh();
	}

	// ══════════════════════════════════════════════════════════
	//  HEADER
	// ══════════════════════════════════════════════════════════

	private JPanel buildHeader()
	{
		JPanel p = new JPanel(new BorderLayout(0, 0));
		p.setBackground(C_SURFACE);
		p.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 0, 1, 0, C_BORDER),
			new EmptyBorder(18, 24, 18, 24)));

		// Titre + sous-titre
		JPanel left = new JPanel();
		left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
		left.setOpaque(false);

		JLabel titre = new JLabel("Panneau de contrôle");
		titre.setFont(new Font("SansSerif", Font.BOLD, 20));
		titre.setForeground(C_TEXT);

		JLabel sous = new JLabel("Serveur gestionNOZ — Planning Global Futura");
		sous.setFont(new Font("SansSerif", Font.PLAIN, 12));
		sous.setForeground(C_MUTED);

		left.add(titre);
		left.add(Box.createVerticalStrut(3));
		left.add(sous);

		// Indicateur live
		JPanel live = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
		live.setOpaque(false);
		JLabel dot = new JLabel("●");
		dot.setFont(new Font("SansSerif", Font.PLAIN, 10));
		dot.setForeground(C_GREEN);
		JLabel liveLabel = new JLabel("EN LIGNE");
		liveLabel.setFont(new Font("SansSerif", Font.BOLD, 11));
		liveLabel.setForeground(C_GREEN);
		live.add(dot);
		live.add(liveLabel);

		p.add(left, BorderLayout.WEST);
		p.add(live, BorderLayout.EAST);
		return p;
	}

	// ══════════════════════════════════════════════════════════
	//  SCROLL PRINCIPAL
	// ══════════════════════════════════════════════════════════

	private JScrollPane buildScroll()
	{
		JPanel corps = new JPanel();
		corps.setBackground(C_BG);
		corps.setLayout(new BoxLayout(corps, BoxLayout.Y_AXIS));
		corps.setBorder(new EmptyBorder(20, 20, 20, 20));

		// ── Rangée de tuiles statut ───────────────────────────
		corps.add(buildSectionLabel("ÉTAT ACTUEL"));
		corps.add(Box.createVerticalStrut(10));
		corps.add(buildTuiles());
		corps.add(Box.createVerticalStrut(24));

		// ── Semaine ───────────────────────────────────────────
		corps.add(buildSectionLabel("SEMAINE DE TRAVAIL"));
		corps.add(Box.createVerticalStrut(10));
		corps.add(buildCardSemaine());
		corps.add(Box.createVerticalStrut(24));

		// ── Sauvegarde ────────────────────────────────────────
		corps.add(buildSectionLabel("SAUVEGARDE"));
		corps.add(Box.createVerticalStrut(10));
		corps.add(buildCardSauvegarde());
		corps.add(Box.createVerticalStrut(24));

		// ── Heures sup ────────────────────────────────────────
		corps.add(buildSectionLabel("HEURES SUPPLÉMENTAIRES"));
		corps.add(Box.createVerticalStrut(10));
		corps.add(buildCardHeuresSup());
		corps.add(Box.createVerticalGlue());

		JScrollPane scroll = new JScrollPane(corps,
			JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
			JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		scroll.getVerticalScrollBar().setUnitIncrement(20);
		scroll.setBorder(BorderFactory.createEmptyBorder());
		scroll.getViewport().setBackground(C_BG);
		// Style scrollbar
		scroll.getVerticalScrollBar().setBackground(C_BG);
		return scroll;
	}

	// ── Label de section ──────────────────────────────────────
	private JLabel buildSectionLabel(String texte)
	{
		JLabel l = new JLabel(texte);
		l.setFont(new Font("SansSerif", Font.BOLD, 10));
		l.setForeground(C_MUTED);
		l.setAlignmentX(Component.LEFT_ALIGNMENT);
		// Lettre espacée style "CAPS LABEL"
		return l;
	}

	// ══════════════════════════════════════════════════════════
	//  TUILES STATUT (rangée du haut)
	// ══════════════════════════════════════════════════════════

	private JPanel buildTuiles()
	{
		JPanel row = new JPanel(new GridLayout(1, 3, 12, 0));
		row.setOpaque(false);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 90));
		row.setAlignmentX(Component.LEFT_ALIGNMENT);

		// Semaine active
		JPanel tSemaine = buildTuile("SEMAINE", "—", C_ACCENT);
		lblSemaine = getTuileValeur(tSemaine);
		row.add(tSemaine);

		// Clients connectés
		JPanel tClients = buildTuile("CLIENTS", "0", C_GREEN);
		lblClients = getTuileValeur(tClients);
		row.add(tClients);

		// Heures sup
		JPanel tHS = buildTuile("HEURES SUP", "non", C_ORANGE);
		lblHeureSup = getTuileValeur(tHS);
		row.add(tHS);

		return row;
	}

	private JPanel buildTuile(String label, String valeurInit, Color accent)
	{
		JPanel p = new JPanel();
		p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
		p.setBackground(C_CARD);
		p.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createCompoundBorder(
				BorderFactory.createMatteBorder(0, 3, 0, 0, accent),
				BorderFactory.createLineBorder(C_BORDER, 1)),
			new EmptyBorder(12, 14, 12, 14)));

		JLabel lbl = new JLabel(label);
		lbl.setFont(new Font("SansSerif", Font.BOLD, 10));
		lbl.setForeground(C_MUTED);
		lbl.setAlignmentX(Component.LEFT_ALIGNMENT);

		JLabel val = new JLabel(valeurInit);
		val.setFont(new Font("SansSerif", Font.BOLD, 18));
		val.setForeground(accent);
		val.setAlignmentX(Component.LEFT_ALIGNMENT);
		val.setName("valeur"); // pour récupération via getTuileValeur

		p.add(lbl);
		p.add(Box.createVerticalStrut(6));
		p.add(val);
		return p;
	}

	/** Récupère le JLabel "valeur" d'une tuile par son nom. */
	private JLabel getTuileValeur(JPanel tuile)
	{
		for (Component c : tuile.getComponents())
			if (c instanceof JLabel && "valeur".equals(c.getName()))
				return (JLabel) c;
		return new JLabel(); // fallback
	}

	// ══════════════════════════════════════════════════════════
	//  CARDS ACTIONS
	// ══════════════════════════════════════════════════════════

	private JPanel buildCardSemaine()
	{
		JPanel card = buildCard();

		// Bouton charger
		JPanel rowCharger = buildActionRow(
			"📂", "Charger une semaine sauvegardée",
			"Bascule tous les clients sur les données du dossier choisi.",
			"Charger…", C_BLUE,
			e -> chargerSemaine());
		card.add(rowCharger);

		card.add(buildSeparateur());

		// Bouton nouvelle semaine
		JPanel rowNouveau = buildActionRow(
			"🆕", "Nouvelle semaine (import Excel)",
			"Importe un fichier XLSX et réinitialise les données serveur.",
			"Importer…", C_ORANGE,
			e -> nouvelleSemaine());
		card.add(rowNouveau);

		return card;
	}

	private JPanel buildCardSauvegarde()
	{
		JPanel card = buildCard();

		JPanel row = buildActionRow(
			"💾", "Sauvegarder la semaine courante",
			"Crée un snapshot dans app/data/enregistrementparsemaine/.",
			"Sauvegarder…", C_GREEN,
			e -> sauvegarder());
		card.add(row);

		return card;
	}

	private JPanel buildCardHeuresSup()
	{
		JPanel card = buildCard();

		JPanel row = buildActionRow(
			"⏱", "Activer / Désactiver les heures supplémentaires",
			"Tous les clients se synchronisent automatiquement dans les 3 secondes.",
			"Basculer", C_ORANGE,
			e -> toggleHeuresSup());
		card.add(row);

		return card;
	}

	// ── Conteneur carte ───────────────────────────────────────
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

	// ── Ligne d'action (icône + texte + bouton) ───────────────
	private JPanel buildActionRow(String icone, String titre, String description,
								  String labelBtn, Color couleurBtn,
								  java.awt.event.ActionListener action)
	{
		JPanel row = new JPanel(new BorderLayout(16, 0));
		row.setOpaque(false);
		row.setBorder(new EmptyBorder(16, 18, 16, 18));
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 80));

		// Gauche : icône + textes
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

		titreRow.add(ico);
		titreRow.add(lblTitre);

		JLabel lblDesc = new JLabel(description);
		lblDesc.setFont(new Font("SansSerif", Font.PLAIN, 11));
		lblDesc.setForeground(C_MUTED);
		lblDesc.setBorder(new EmptyBorder(0, 6, 0, 0));

		left.add(titreRow);
		left.add(Box.createVerticalStrut(4));
		left.add(lblDesc);

		// Droite : bouton
		JButton btn = new JButton(labelBtn);
		btn.setFont(new Font("SansSerif", Font.BOLD, 12));
		btn.setBackground(couleurBtn);
		btn.setForeground(Color.WHITE);
		btn.setFocusPainted(false);
		btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
		btn.setBorder(new EmptyBorder(9, 18, 9, 18));
		btn.setOpaque(true);
		btn.addActionListener(action);
		// Hover effect
		Color hoverColor = couleurBtn.darker();
		btn.addMouseListener(new java.awt.event.MouseAdapter()
		{
			public void mouseEntered(java.awt.event.MouseEvent e) { btn.setBackground(hoverColor); }
			public void mouseExited (java.awt.event.MouseEvent e) { btn.setBackground(couleurBtn); }
		});

		JPanel btnWrapper = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
		btnWrapper.setOpaque(false);
		btnWrapper.add(btn);

		row.add(left,       BorderLayout.CENTER);
		row.add(btnWrapper, BorderLayout.EAST);
		return row;
	}

	// ── Séparateur fin ────────────────────────────────────────
	private JPanel buildSeparateur()
	{
		JPanel sep = new JPanel();
		sep.setBackground(C_BORDER);
		sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
		sep.setPreferredSize(new Dimension(0, 1));
		return sep;
	}

	// ══════════════════════════════════════════════════════════
	//  FOOTER
	// ══════════════════════════════════════════════════════════

	private JPanel buildFooter()
	{
		JPanel p = new JPanel(new BorderLayout(0, 0));
		p.setBackground(C_SURFACE);
		p.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(1, 0, 0, 0, C_BORDER),
			new EmptyBorder(10, 24, 10, 24)));

		// Indicateur serveur actif
		JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
		left.setOpaque(false);
		JLabel dot = new JLabel("●");
		dot.setFont(new Font("SansSerif", Font.PLAIN, 10));
		dot.setForeground(C_GREEN);
		JLabel txtServeur = new JLabel("Serveur actif — Port 8080");
		txtServeur.setFont(new Font("SansSerif", Font.PLAIN, 11));
		txtServeur.setForeground(C_MUTED);
		left.add(dot);
		left.add(txtServeur);

		// IP à droite
		lblIP = new JLabel("IP : " + detecterIP());
		lblIP.setFont(new Font("SansSerif", Font.BOLD, 11));
		lblIP.setForeground(C_ACCENT);

		p.add(left,  BorderLayout.WEST);
		p.add(lblIP, BorderLayout.EAST);
		return p;
	}

	// ══════════════════════════════════════════════════════════
	//  ACTIONS
	// ══════════════════════════════════════════════════════════

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
			refresh();
			JOptionPane.showMessageDialog(this,
				"Semaine chargée. Les clients se synchroniseront dans les 3 secondes.",
				"Chargement OK", JOptionPane.INFORMATION_MESSAGE);
		}
		catch (Exception ex)
		{
			JOptionPane.showMessageDialog(this, "Erreur :\n" + ex.getMessage(),
				"Erreur", JOptionPane.ERROR_MESSAGE);
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
			JOptionPane.showMessageDialog(this, "Erreur :\n" + ex.getMessage(),
				"Erreur", JOptionPane.ERROR_MESSAGE);
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

		try
		{
			serveur.sauvegarderSemaine(fc.getSelectedFile().getAbsolutePath(), numSemaine.trim());
			JOptionPane.showMessageDialog(this, "Sauvegarde effectuée dans S" + numSemaine.trim(),
				"Sauvegarde OK", JOptionPane.INFORMATION_MESSAGE);
		}
		catch (Exception ex)
		{
			JOptionPane.showMessageDialog(this, "Erreur :\n" + ex.getMessage(),
				"Erreur", JOptionPane.ERROR_MESSAGE);
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

	// ══════════════════════════════════════════════════════════
	//  RAFRAÎCHISSEMENT
	// ══════════════════════════════════════════════════════════

	private void refresh()
	{
		// Semaine
		String sem = serveur.getSemaineActive();
		String semTxt = (sem != null && !sem.isBlank()) ? sem : "—";
		lblSemaine.setText(semTxt);

		// Clients
		int nb = serveur.getNbClientsConnectes();
		lblClients.setText(String.valueOf(nb));
		lblClients.setForeground(nb > 0 ? C_GREEN : C_MUTED);

		// Heures sup
		boolean hs = app.metier.PlanningGlobal.estHeureSup;
		lblHeureSup.setText(hs ? "OUI" : "non");
		lblHeureSup.setForeground(hs ? C_ORANGE : C_MUTED);
	}

	// ══════════════════════════════════════════════════════════
	//  UTILITAIRES
	// ══════════════════════════════════════════════════════════

	private String detecterIP()
	{
		try
		{
			Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
			while (interfaces.hasMoreElements())
			{
				NetworkInterface ni = interfaces.nextElement();
				Enumeration<InetAddress> adresses = ni.getInetAddresses();
				while (adresses.hasMoreElements())
				{
					InetAddress addr = adresses.nextElement();
					if (!addr.isLoopbackAddress() && addr instanceof Inet4Address)
						return addr.getHostAddress();
				}
			}
		}
		catch (Exception ignored) {}
		return "inconnue";
	}
}