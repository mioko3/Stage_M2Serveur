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
 * Demande l'IP du serveur, teste la connexion, puis lance ControleurClient.
 * Point d'entrée client : java -cp ... app.ControleurClient
 */
public class FenetreConnexionClient extends JFrame implements ActionListener
{
	private JTextField txtIP;
	private JButton    btnConnecter;
	private JLabel     lblStatut;

	public FenetreConnexionClient()
	{
		setTitle("Planning Global Futura — Connexion Réseau");
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setSize(420, 280);
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
		carte.setPreferredSize(new Dimension(380, 240));
		carte.setBackground(new Color(28, 28, 40));
		carte.setLayout(new BoxLayout(carte, BoxLayout.Y_AXIS));
		carte.setBorder(new EmptyBorder(30, 40, 30, 40));

		JLabel titre = new JLabel("GLOBAL FUTURA — CLIENT RÉSEAU");
		titre.setAlignmentX(Component.CENTER_ALIGNMENT);
		titre.setFont(new Font("SansSerif", Font.BOLD, 16));
		titre.setForeground(new Color(120, 170, 255));
		carte.add(titre);
		carte.add(Box.createRigidArea(new Dimension(0, 20)));

		JLabel lblIP = new JLabel("IP du serveur (ex: 192.168.1.10) :");
		lblIP.setAlignmentX(Component.CENTER_ALIGNMENT);
		lblIP.setForeground(Color.WHITE);
		lblIP.setFont(new Font("SansSerif", Font.BOLD, 12));
		carte.add(lblIP);
		carte.add(Box.createRigidArea(new Dimension(0, 8)));

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

		lblStatut.setText("Connexion en cours…");
		lblStatut.setForeground(new Color(180, 180, 100));
		btnConnecter.setEnabled(false);
		txtIP.setEnabled(false);

		new Thread(() ->
		{
			try
			{
				HttpClient http = HttpClient.newBuilder()
					.connectTimeout(Duration.ofSeconds(5)).build();
				HttpRequest req = HttpRequest.newBuilder()
					.uri(URI.create("http://" + ip + ":8080/lots"))
					.timeout(Duration.ofSeconds(5)).GET().build();
				HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());

				if (resp.statusCode() == 200)
				{
					SwingUtilities.invokeLater(() -> {
						dispose();
						new ControleurClient(ip);
					});
				}
				else
				{
					SwingUtilities.invokeLater(() -> {
						lblStatut.setText("Erreur serveur : " + resp.statusCode());
						lblStatut.setForeground(new Color(255, 120, 120));
						btnConnecter.setEnabled(true);
						txtIP.setEnabled(true);
					});
				}
			}
			catch (java.net.ConnectException ex)
			{
				SwingUtilities.invokeLater(() -> {
					lblStatut.setText("Connexion refusée — serveur démarré ?");
					lblStatut.setForeground(new Color(255, 120, 120));
					btnConnecter.setEnabled(true);
					txtIP.setEnabled(true);
				});
			}
			catch (Exception ex)
			{
				SwingUtilities.invokeLater(() -> {
					lblStatut.setText("Erreur : " + ex.getMessage());
					lblStatut.setForeground(new Color(255, 120, 120));
					btnConnecter.setEnabled(true);
					txtIP.setEnabled(true);
				});
			}
		}).start();
	}
}
