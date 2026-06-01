package app.ihm.login;

import app.ControleurClient;
import java.awt.*;
import java.awt.event.*;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

/**
 * ══════════════════════════════════════════════════════════════
 *  FenetreConnexionClient — v4 avec mot de passe + création compte
 *
 *  Identique au panneau web :
 *   - Champ identifiant
 *   - Champ mot de passe (masqué)
 *   - Bouton SE CONNECTER
 *   - Lien "Créer un compte" → FenetreCreationCompte
 * ══════════════════════════════════════════════════════════════
 */
public class FenetreConnexionClient extends JFrame implements ActionListener
{
	private JTextField     txtIP;
	private JTextField     txtIdentifiant;
	private JPasswordField txtMdp;
	private JButton        btnConnecter;
	private JLabel         lblStatut;

	public FenetreConnexionClient()
	{
		setTitle("Planning Global Futura — Connexion Réseau");
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setSize(440, 420);
		setLocationRelativeTo(null);
		setResizable(false);
		setLayout(new BorderLayout());
		getContentPane().setBackground(new Color(18, 18, 28));
		add(construireCarte(), BorderLayout.CENTER);
		setVisible(true);

		// Focus sur identifiant au démarrage
		SwingUtilities.invokeLater(() -> txtIdentifiant.requestFocusInWindow());
	}

	private JPanel construireCarte()
	{
		JPanel fond = new JPanel(new GridBagLayout());
		fond.setBackground(new Color(18, 18, 28));

		JPanel carte = new JPanel();
		carte.setPreferredSize(new Dimension(390, 390));
		carte.setBackground(new Color(28, 28, 40));
		carte.setLayout(new BoxLayout(carte, BoxLayout.Y_AXIS));
		carte.setBorder(new EmptyBorder(28, 40, 28, 40));

		// Titre
		JLabel titre = new JLabel("GLOBAL FUTURA — CLIENT RÉSEAU");
		titre.setAlignmentX(Component.CENTER_ALIGNMENT);
		titre.setFont(new Font("SansSerif", Font.BOLD, 15));
		titre.setForeground(new Color(120, 170, 255));
		carte.add(titre);
		carte.add(Box.createRigidArea(new Dimension(0, 22)));

		// Identifiant
		carte.add(champLabel("Identifiant :"));
		carte.add(Box.createRigidArea(new Dimension(0, 6)));
		txtIdentifiant = champTexte("");
		txtIdentifiant.addActionListener(this);
		carte.add(txtIdentifiant);
		carte.add(Box.createRigidArea(new Dimension(0, 12)));

		// Mot de passe
		carte.add(champLabel("Mot de passe :"));
		carte.add(Box.createRigidArea(new Dimension(0, 6)));
		txtMdp = champPassword();
		txtMdp.addActionListener(this);
		carte.add(txtMdp);
		carte.add(Box.createRigidArea(new Dimension(0, 12)));

		// IP
		carte.add(champLabel("IP du serveur (ex: 192.168.1.10) :"));
		carte.add(Box.createRigidArea(new Dimension(0, 6)));
		txtIP = champTexte("localhost");
		txtIP.addActionListener(this);
		carte.add(txtIP);
		carte.add(Box.createRigidArea(new Dimension(0, 16)));

		// Bouton connexion
		btnConnecter = new JButton("SE CONNECTER");
		btnConnecter.setAlignmentX(Component.CENTER_ALIGNMENT);
		btnConnecter.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
		btnConnecter.setFocusPainted(false);
		btnConnecter.setBackground(new Color(70, 120, 255));
		btnConnecter.setForeground(Color.WHITE);
		btnConnecter.setFont(new Font("SansSerif", Font.BOLD, 14));
		btnConnecter.setBorder(new EmptyBorder(10, 20, 10, 20));
		btnConnecter.addActionListener(this);
		carte.add(btnConnecter);
		carte.add(Box.createRigidArea(new Dimension(0, 10)));

		// Statut
		lblStatut = new JLabel(" ");
		lblStatut.setAlignmentX(Component.CENTER_ALIGNMENT);
		lblStatut.setForeground(new Color(255, 120, 120));
		lblStatut.setFont(new Font("SansSerif", Font.ITALIC, 12));
		carte.add(lblStatut);
		carte.add(Box.createRigidArea(new Dimension(0, 12)));

		// Séparateur
		JSeparator sep = new JSeparator();
		sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
		sep.setForeground(new Color(60, 60, 80));
		carte.add(sep);
		carte.add(Box.createRigidArea(new Dimension(0, 10)));

		// Lien création compte
		JLabel lienCreer = new JLabel("<html>Pas encore de compte ? <u style='color:#7aa4ff'>Créer un compte</u></html>");
		lienCreer.setAlignmentX(Component.CENTER_ALIGNMENT);
		lienCreer.setForeground(new Color(150, 150, 180));
		lienCreer.setFont(new Font("SansSerif", Font.PLAIN, 12));
		lienCreer.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		lienCreer.addMouseListener(new MouseAdapter()
		{
			@Override public void mouseClicked(MouseEvent e)
			{
				String ip = txtIP.getText().trim();
				if (ip.isEmpty()) ip = "localhost";
				new FenetreCreationCompte(FenetreConnexionClient.this, ip);
			}
		});
		carte.add(lienCreer);

		fond.add(carte);
		return fond;
	}

	@Override
	public void actionPerformed(ActionEvent e) { tenterConnexion(); }

	private void tenterConnexion()
	{
		String ip          = txtIP.getText().trim();
		String identifiant = txtIdentifiant.getText().trim().toUpperCase();
		String mdp         = new String(txtMdp.getPassword());

		if (ip.isEmpty())          { setStatut("Entrez une adresse IP.", true);  return; }
		if (identifiant.isEmpty()) { setStatut("Entrez un identifiant.", true);  return; }
		if (mdp.isEmpty())         { setStatut("Entrez un mot de passe.", true); return; }

		setStatut("Connexion en cours…", false);
		btnConnecter.setEnabled(false);
		txtIP.setEnabled(false);
		txtIdentifiant.setEnabled(false);
		txtMdp.setEnabled(false);

		final String ipFinal  = ip;
		final String idFinal  = identifiant;
		final String mdpFinal = mdp;

		new Thread(() ->
		{
			try
			{
				HttpClient http = HttpClient.newBuilder()
					.connectTimeout(Duration.ofSeconds(5)).build();

				// POST /login avec identifiant + motDePasse
				String corpsLogin = "{\"identifiant\":" + escJson(idFinal)
					+ ",\"motDePasse\":" + escJson(mdpFinal) + "}";

				HttpRequest reqLogin = HttpRequest.newBuilder()
					.uri(URI.create("http://" + ipFinal + ":8082/login"))
					.timeout(Duration.ofSeconds(5))
					.header("Content-Type", "application/json")
					.POST(HttpRequest.BodyPublishers.ofString(corpsLogin, StandardCharsets.UTF_8))
					.build();

				HttpResponse<String> resp = http.send(reqLogin, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

				if (resp.statusCode() == 429)
				{
					setStatut("Trop de tentatives. Réessayez dans 5 minutes.", true);
					return;
				}
				if (resp.statusCode() == 403)
				{
					setStatut("Compte en attente de validation par un admin.", true);
					return;
				}
				if (resp.statusCode() == 401)
				{
					setStatut("Identifiant ou mot de passe incorrect.", true);
					return;
				}
				if (resp.statusCode() != 200)
				{
					setStatut("Erreur serveur : " + resp.statusCode(), true);
					return;
				}

				String corps     = resp.body();
				String token     = extraireChaine(corps, "token");
				boolean accesPAM = corps.contains("\"accesPAM\":true");

				if (token == null || token.isBlank())
				{
					setStatut("Réponse du serveur invalide.", true);
					return;
				}

				final String  tokenFinal    = token;
				final boolean accesPAMFinal = accesPAM;

				SwingUtilities.invokeLater(() -> {
					dispose();
					new ControleurClient(ipFinal, idFinal, accesPAMFinal, tokenFinal);
				});
			}
			catch (java.net.ConnectException ex)
			{
				setStatut("Connexion refusée — serveur démarré ?", true);
			}
			catch (Exception ex)
			{
				setStatut("Erreur : " + ex.getMessage(), true);
			}
		}).start();
	}

	private void setStatut(String msg, boolean erreur)
	{
		SwingUtilities.invokeLater(() -> {
			lblStatut.setText(msg);
			lblStatut.setForeground(erreur ? new Color(255, 120, 120) : new Color(100, 200, 100));
			btnConnecter.setEnabled(true);
			txtIP.setEnabled(true);
			txtIdentifiant.setEnabled(true);
			txtMdp.setEnabled(true);
		});
	}

	// ── Helpers ───────────────────────────────────────────────────────────

	private static String extraireChaine(String json, String cle)
	{
		String p = "\"" + cle + "\":\"";
		int pos = json.indexOf(p);
		if (pos < 0) return null;
		pos += p.length();
		int end = json.indexOf('"', pos);
		return end < 0 ? null : json.substring(pos, end);
	}

	private static String escJson(String s)
	{
		if (s == null) return "\"\"";
		return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
	}

	private JLabel champLabel(String texte)
	{
		JLabel l = new JLabel(texte);
		l.setAlignmentX(Component.LEFT_ALIGNMENT);
		l.setForeground(Color.WHITE);
		l.setFont(new Font("SansSerif", Font.BOLD, 12));
		return l;
	}

	private JTextField champTexte(String defaut)
	{
		JTextField tf = new JTextField(defaut);
		tf.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
		tf.setAlignmentX(Component.LEFT_ALIGNMENT);
		tf.setBackground(new Color(40, 40, 55));
		tf.setForeground(Color.WHITE);
		tf.setCaretColor(Color.WHITE);
		tf.setHorizontalAlignment(JTextField.LEFT);
		tf.setFont(new Font("SansSerif", Font.PLAIN, 13));
		tf.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(new Color(70, 70, 100), 1),
			new EmptyBorder(4, 8, 4, 8)));
		return tf;
	}

	private JPasswordField champPassword()
	{
		JPasswordField pf = new JPasswordField();
		pf.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
		pf.setAlignmentX(Component.LEFT_ALIGNMENT);
		pf.setBackground(new Color(40, 40, 55));
		pf.setForeground(Color.WHITE);
		pf.setCaretColor(Color.WHITE);
		pf.setFont(new Font("SansSerif", Font.PLAIN, 13));
		pf.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(new Color(70, 70, 100), 1),
			new EmptyBorder(4, 8, 4, 8)));
		return pf;
	}
}