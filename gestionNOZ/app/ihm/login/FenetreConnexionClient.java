package app.ihm.login;

import app.ControleurClient;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

/**
 * Fenêtre de connexion RÉSEAU.
 *
 * Demande :
 *   - L'adresse IP du serveur
 *   - Un identifiant :
 *       "PAM"          → accès complet (affectation + désynchronisation)
 *       Nom de société → accès lecture seule (PanelAffectation bloqué)
 *
 * Point d'entrée client : java -cp ... app.ControleurClient
 */
public class FenetreConnexionClient extends JFrame implements ActionListener
{
	private JTextField txtIP;
	private JTextField txtIdentifiant;
	private JButton    btnConnecter;
	private JLabel     lblStatut;

	// Sociétés connues récupérées au moment de la connexion (pour validation)
	private java.util.List<String> nomsocietes = new java.util.ArrayList<>();

	public FenetreConnexionClient()
	{
		setTitle("Planning Global Futura — Connexion Réseau");
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setSize(440, 340);
		setLocationRelativeTo(null);
		setResizable(false);
		setLayout(new BorderLayout());
		getContentPane().setBackground(new Color(18, 18, 28));
		add(construireCarte(), BorderLayout.CENTER);
		setVisible(true);
	}

	private JPanel construireCarte()
	{
		JPanel fond = new JPanel(new GridBagLayout());
		fond.setBackground(new Color(18, 18, 28));

		JPanel carte = new JPanel();
		carte.setPreferredSize(new Dimension(390, 300));
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
		carte.add(champLabel("Identifiant (Nom de société) :"));
		carte.add(Box.createRigidArea(new Dimension(0, 6)));
		txtIdentifiant = champTexte("");
		txtIdentifiant.addActionListener(this);
		carte.add(txtIdentifiant);
		carte.add(Box.createRigidArea(new Dimension(0, 18)));

		// IP
		carte.add(champLabel("IP du serveur (ex: 192.168.1.10) :"));
		carte.add(Box.createRigidArea(new Dimension(0, 6)));
		txtIP = champTexte("localhost");
		txtIP.addActionListener(this);
		carte.add(txtIP);
		carte.add(Box.createRigidArea(new Dimension(0, 14)));

		// Bouton
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

		fond.add(carte);
		return fond;
	}

	@Override
	public void actionPerformed(ActionEvent e) { tenterConnexion(); }

	private void tenterConnexion()
	{
		String ip          = txtIP.getText().trim();
		String identifiant = txtIdentifiant.getText().trim().toUpperCase();

		if (ip.isEmpty())          { lblStatut.setText("Entrez une adresse IP.");    return; }
		if (identifiant.isEmpty()) { lblStatut.setText("Entrez un identifiant.");    return; }

		lblStatut.setText("Connexion en cours…");
		lblStatut.setForeground(new Color(180, 180, 100));
		btnConnecter.setEnabled(false);
		txtIP.setEnabled(false);
		txtIdentifiant.setEnabled(false);

		final String ipFinal  = ip;
		final String idFinal  = identifiant;

		new Thread(() ->
		{
			try
			{
				HttpClient http = HttpClient.newBuilder()
					.connectTimeout(Duration.ofSeconds(5)).build();

				// 1. Tester la connexion et récupérer les noms de sociétés
				HttpRequest req = HttpRequest.newBuilder()
					.uri(URI.create("http://" + ipFinal + ":8080/societes"))
					.timeout(Duration.ofSeconds(5)).GET().build();
				HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());

				if (resp.statusCode() != 200)
				{
					setStatut("Erreur serveur : " + resp.statusCode(), true);
					return;
				}

				// 2. Extraire les noms de sociétés pour validation identifiant
				java.util.List<String> societes = extraireNomsSocietes(resp.body());

				// 3. Valider l'identifiant
				boolean estPAM     = idFinal.equals("PAM");
				boolean estSociete = societes.stream()
					.anyMatch(n -> n.toUpperCase().equals(idFinal));

				if (!estPAM && !estSociete)
				{
					setStatut("Identifiant inconnu : " + idFinal, true);
					return;
				}

				// 4. Lancer l'application
				final boolean accesPAM = estPAM;
				SwingUtilities.invokeLater(() -> {
					dispose();
					new ControleurClient(ipFinal, idFinal, accesPAM);
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
		});
	}

	/**
	 * Parse très simple pour extraire les "nom" des sociétés depuis le JSON.
	 * Ex: [{"nom":"EUP",...},{"nom":"PA",...}]  →  ["EUP", "PA"]
	 */
	private java.util.List<String> extraireNomsSocietes(String json)
	{
		java.util.List<String> noms = new java.util.ArrayList<>();
		int pos = 0;
		while ((pos = json.indexOf("\"nom\":", pos)) >= 0)
		{
			pos += 6;
			while (pos < json.length() && json.charAt(pos) != '"') pos++;
			if (pos >= json.length()) break;
			pos++; // sauter le guillemet ouvrant
			int end = json.indexOf('"', pos);
			if (end < 0) break;
			noms.add(json.substring(pos, end));
			pos = end + 1;
		}
		return noms;
	}

	// ── Helpers UI ────────────────────────────────────────────────────────

	private JLabel champLabel(String texte)
	{
		JLabel l = new JLabel(texte);
		l.setAlignmentX(Component.CENTER_ALIGNMENT);
		l.setForeground(Color.WHITE);
		l.setFont(new Font("SansSerif", Font.BOLD, 12));
		return l;
	}

	private JTextField champTexte(String defaut)
	{
		JTextField tf = new JTextField(defaut);
		tf.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
		tf.setBackground(new Color(40, 40, 55));
		tf.setForeground(Color.WHITE);
		tf.setCaretColor(Color.WHITE);
		tf.setHorizontalAlignment(JTextField.CENTER);
		tf.setFont(new Font("SansSerif", Font.PLAIN, 14));
		tf.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(new Color(70, 70, 100), 2),
			new EmptyBorder(5, 10, 5, 10)));
		return tf;
	}
}