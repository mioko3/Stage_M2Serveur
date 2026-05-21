package app.ihm.login;

import app.ControleurClient;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

/**
 * Fenêtre de connexion RÉSEAU.
 * Demande l'IP du serveur et lance ControleurClient.
 *
 * Point d'entrée : java -cp ... app.ihm.login.FenetreConnexionClient
 *              OU : java -cp ... app.ControleurClient [IP]
 */
public class FenetreConnexionClient extends JFrame implements ActionListener
{
	private JTextField txtIP;
	private JTextField txtPort;
	private JButton    btnConnecter;
	private JLabel     lblStatut;

	public FenetreConnexionClient()
	{
		setTitle("Planning Global Futura — Connexion Réseau");
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setSize(420, 300);
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
		carte.setPreferredSize(new Dimension(380, 260));
		carte.setBackground(new Color(28, 28, 40));
		carte.setLayout(new BoxLayout(carte, BoxLayout.Y_AXIS));
		carte.setBorder(new EmptyBorder(30, 40, 30, 40));

		// Titre
		JLabel titre = new JLabel("GLOBAL FUTURA — CLIENT");
		titre.setAlignmentX(Component.CENTER_ALIGNMENT);
		titre.setFont(new Font("SansSerif", Font.BOLD, 20));
		titre.setForeground(new Color(120, 170, 255));
		carte.add(titre);
		carte.add(Box.createRigidArea(new Dimension(0, 6)));

		JLabel sous = new JLabel("Connexion au serveur");
		sous.setAlignmentX(Component.CENTER_ALIGNMENT);
		sous.setFont(new Font("SansSerif", Font.PLAIN, 13));
		sous.setForeground(new Color(180, 180, 190));
		carte.add(sous);
		carte.add(Box.createRigidArea(new Dimension(0, 24)));

		// Champ IP
		JLabel lblIP = new JLabel("IP du serveur :");
		lblIP.setAlignmentX(Component.CENTER_ALIGNMENT);
		lblIP.setForeground(Color.WHITE);
		lblIP.setFont(new Font("SansSerif", Font.BOLD, 12));
		carte.add(lblIP);
		carte.add(Box.createRigidArea(new Dimension(0, 6)));

		txtIP = new JTextField("localhost");
		txtIP.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
		txtIP.setBackground(new Color(40, 40, 55));
		txtIP.setForeground(Color.WHITE);
		txtIP.setCaretColor(Color.WHITE);
		txtIP.setHorizontalAlignment(JTextField.CENTER);
		txtIP.setFont(new Font("SansSerif", Font.PLAIN, 15));
		txtIP.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(new Color(70, 70, 100), 2),
			new EmptyBorder(6, 10, 6, 10)));
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
	public void actionPerformed(ActionEvent e)
	{
		String ip = txtIP.getText().trim();
		if (ip.isEmpty()) { lblStatut.setText("Entrez une adresse IP."); return; }

		lblStatut.setText("Connexion en cours...");
		lblStatut.setForeground(new Color(180, 180, 100));
		btnConnecter.setEnabled(false);

		// Test de connexion avant de lancer l'app
		new Thread(() ->
		{
			try
			{
				java.net.http.HttpClient testHttp = java.net.http.HttpClient.newHttpClient();
				java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder()
					.uri(java.net.URI.create("http://" + ip + ":8080/lots"))
					.GET()
					.timeout(java.time.Duration.ofSeconds(5))
					.build();
				java.net.http.HttpResponse<String> resp = testHttp.send(req,
					java.net.http.HttpResponse.BodyHandlers.ofString());

				if (resp.statusCode() == 200)
				{
					SwingUtilities.invokeLater(() ->
					{
						dispose();
						new ControleurClient(ip);
					});
				}
				else
				{
					SwingUtilities.invokeLater(() ->
					{
						lblStatut.setText("Serveur répond avec erreur " + resp.statusCode());
						lblStatut.setForeground(new Color(255, 120, 120));
						btnConnecter.setEnabled(true);
					});
				}
			}
			catch (java.net.ConnectException ce)
			{
				SwingUtilities.invokeLater(() ->
				{
					lblStatut.setText("Connexion refusée — serveur démarré ?");
					lblStatut.setForeground(new Color(255, 120, 120));
					btnConnecter.setEnabled(true);
				});
			}
			catch (java.net.http.HttpTimeoutException te)
			{
				SwingUtilities.invokeLater(() ->
				{
					lblStatut.setText("Timeout — IP incorrecte ou pare-feu ?");
					lblStatut.setForeground(new Color(255, 120, 120));
					btnConnecter.setEnabled(true);
				});
			}
			catch (Exception ex)
			{
				SwingUtilities.invokeLater(() ->
				{
					lblStatut.setText("Erreur : " + ex.getMessage());
					lblStatut.setForeground(new Color(255, 120, 120));
					btnConnecter.setEnabled(true);
				});
			}
		}).start();
	}

	public static void main(String[] args)
	{
		// Si une IP est passée en argument, connexion directe
		if (args.length > 0)
			SwingUtilities.invokeLater(() -> new ControleurClient(args[0]));
		else
			SwingUtilities.invokeLater(FenetreConnexionClient::new);
	}
}
