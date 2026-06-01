package app.ihm.login;

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
 *  FenetreCreationCompte — Portage exact du panneau web
 *
 *  Flux :
 *   1. L'utilisateur saisit identifiant + mot de passe + confirmation
 *   2. POST /creer-compte → serveur crée une demande "attente"
 *   3. Affiche message "En attente de validation"
 *   4. Lien pour revenir à la connexion
 * ══════════════════════════════════════════════════════════════
 */
public class FenetreCreationCompte extends JDialog
{
	// ── Couleurs identiques à FenetreConnexionClient ──────────────────────
	private static final Color BG_DARK   = new Color(18, 18, 28);
	private static final Color BG_CARD   = new Color(28, 28, 40);
	private static final Color C_BLUE    = new Color(70, 120, 255);
	private static final Color C_GREEN   = new Color(46, 160, 67);
	private static final Color C_RED     = new Color(255, 120, 120);
	private static final Color C_WHITE   = Color.WHITE;
	private static final Color C_MUTED   = new Color(180, 180, 200);

	private final String ipServeur;

	private JTextField     txtIdentifiant;
	private JPasswordField txtMdp;
	private JPasswordField txtConfirm;
	private JButton        btnCreer;
	private JLabel         lblStatut;

	// ══════════════════════════════════════════════════════════════════════
	//  CONSTRUCTEUR
	// ══════════════════════════════════════════════════════════════════════

	public FenetreCreationCompte(Window parent, String ipServeur)
	{
		super(parent, "Créer un compte — Planning Global Futura", ModalityType.APPLICATION_MODAL);
		this.ipServeur = ipServeur;

		setSize(440, 420);
		setLocationRelativeTo(parent);
		setResizable(false);
		setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
		getContentPane().setBackground(BG_DARK);
		setLayout(new GridBagLayout());

		add(construireCarte());
		setVisible(true);
	}

	// ══════════════════════════════════════════════════════════════════════
	//  CONSTRUCTION UI
	// ══════════════════════════════════════════════════════════════════════

	private JPanel construireCarte()
	{
		JPanel carte = new JPanel();
		carte.setPreferredSize(new Dimension(390, 380));
		carte.setBackground(BG_CARD);
		carte.setLayout(new BoxLayout(carte, BoxLayout.Y_AXIS));
		carte.setBorder(new EmptyBorder(28, 40, 28, 40));

		// Titre
		JLabel titre = new JLabel("CRÉER UN COMPTE");
		titre.setAlignmentX(Component.CENTER_ALIGNMENT);
		titre.setFont(new Font("SansSerif", Font.BOLD, 15));
		titre.setForeground(C_BLUE);
		carte.add(titre);

		JLabel sousTitre = new JLabel("Le compte sera non-admin — validation requise");
		sousTitre.setAlignmentX(Component.CENTER_ALIGNMENT);
		sousTitre.setFont(new Font("SansSerif", Font.ITALIC, 11));
		sousTitre.setForeground(C_MUTED);
		carte.add(Box.createRigidArea(new Dimension(0, 4)));
		carte.add(sousTitre);
		carte.add(Box.createRigidArea(new Dimension(0, 20)));

		// Identifiant
		carte.add(champLabel("Identifiant :"));
		carte.add(Box.createRigidArea(new Dimension(0, 5)));
		txtIdentifiant = champTexte("");
		carte.add(txtIdentifiant);
		carte.add(Box.createRigidArea(new Dimension(0, 12)));

		// Mot de passe
		carte.add(champLabel("Mot de passe :"));
		carte.add(Box.createRigidArea(new Dimension(0, 5)));
		txtMdp = champPassword();
		carte.add(txtMdp);
		carte.add(Box.createRigidArea(new Dimension(0, 12)));

		// Confirmation
		carte.add(champLabel("Confirmer le mot de passe :"));
		carte.add(Box.createRigidArea(new Dimension(0, 5)));
		txtConfirm = champPassword();
		txtConfirm.addActionListener(e -> tenterCreation());
		carte.add(txtConfirm);
		carte.add(Box.createRigidArea(new Dimension(0, 18)));

		// Bouton
		btnCreer = new JButton("Envoyer la demande");
		btnCreer.setAlignmentX(Component.CENTER_ALIGNMENT);
		btnCreer.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
		btnCreer.setFocusPainted(false);
		btnCreer.setBackground(C_GREEN);
		btnCreer.setForeground(C_WHITE);
		btnCreer.setFont(new Font("SansSerif", Font.BOLD, 13));
		btnCreer.addActionListener(e -> tenterCreation());
		carte.add(btnCreer);
		carte.add(Box.createRigidArea(new Dimension(0, 10)));

		// Statut
		lblStatut = new JLabel(" ");
		lblStatut.setAlignmentX(Component.CENTER_ALIGNMENT);
		lblStatut.setForeground(C_RED);
		lblStatut.setFont(new Font("SansSerif", Font.ITALIC, 12));
		carte.add(lblStatut);
		carte.add(Box.createRigidArea(new Dimension(0, 8)));

		// Lien retour
		JLabel lienRetour = new JLabel("<html><u>← Retour à la connexion</u></html>");
		lienRetour.setAlignmentX(Component.CENTER_ALIGNMENT);
		lienRetour.setForeground(C_BLUE);
		lienRetour.setFont(new Font("SansSerif", Font.PLAIN, 12));
		lienRetour.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		lienRetour.addMouseListener(new MouseAdapter()
		{
			@Override public void mouseClicked(MouseEvent e) { dispose(); }
		});
		carte.add(lienRetour);

		return carte;
	}

	// ══════════════════════════════════════════════════════════════════════
	//  LOGIQUE
	// ══════════════════════════════════════════════════════════════════════

	private void tenterCreation()
	{
		String id   = txtIdentifiant.getText().trim();
		String mdp  = new String(txtMdp.getPassword());
		String conf = new String(txtConfirm.getPassword());

		// Validations locales
		if (id.isEmpty() || mdp.isEmpty() || conf.isEmpty())
		{ setStatut("Remplissez tous les champs.", true); return; }
		if (mdp.length() < 4)
		{ setStatut("Mot de passe trop court (4 caractères min).", true); return; }
		if (!mdp.equals(conf))
		{ setStatut("Les mots de passe ne correspondent pas.", true); return; }

		setStatut("Envoi en cours…", false);
		btnCreer.setEnabled(false);

		final String idFinal  = id;
		final String mdpFinal = mdp;

		new Thread(() ->
		{
			try
			{
				HttpClient http = HttpClient.newBuilder()
					.connectTimeout(Duration.ofSeconds(5)).build();

				String corps = "{\"identifiant\":" + escJson(idFinal)
					+ ",\"motDePasse\":" + escJson(mdpFinal) + "}";

				HttpRequest req = HttpRequest.newBuilder()
					.uri(URI.create("http://" + ipServeur + ":8082/creer-compte"))
					.timeout(Duration.ofSeconds(5))
					.header("Content-Type", "application/json")
					.POST(HttpRequest.BodyPublishers.ofString(corps, StandardCharsets.UTF_8))
					.build();

				HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

				if (resp.statusCode() == 200)
				{
					SwingUtilities.invokeLater(() -> afficherSucces(idFinal));
				}
				else
				{
					// Extraire message d'erreur du JSON
					String errMsg = extraireChaine(resp.body(), "err");
					if (errMsg == null || errMsg.isBlank()) errMsg = "Erreur " + resp.statusCode();
					final String msg = errMsg;
					SwingUtilities.invokeLater(() -> {
						setStatut(msg, true);
						btnCreer.setEnabled(true);
					});
				}
			}
			catch (java.net.ConnectException ex)
			{
				SwingUtilities.invokeLater(() -> {
					setStatut("Connexion refusée — serveur démarré ?", true);
					btnCreer.setEnabled(true);
				});
			}
			catch (Exception ex)
			{
				SwingUtilities.invokeLater(() -> {
					setStatut("Erreur : " + ex.getMessage(), true);
					btnCreer.setEnabled(true);
				});
			}
		}).start();
	}

	private void afficherSucces(String identifiant)
	{
		// Remplacer le contenu de la carte par un message de succès
		getContentPane().removeAll();
		getContentPane().setLayout(new GridBagLayout());

		JPanel carte = new JPanel();
		carte.setPreferredSize(new Dimension(390, 260));
		carte.setBackground(BG_CARD);
		carte.setLayout(new BoxLayout(carte, BoxLayout.Y_AXIS));
		carte.setBorder(new EmptyBorder(40, 40, 40, 40));

		JLabel ico = new JLabel("⏳");
		ico.setFont(new Font("SansSerif", Font.PLAIN, 36));
		ico.setAlignmentX(Component.CENTER_ALIGNMENT);
		carte.add(ico);
		carte.add(Box.createRigidArea(new Dimension(0, 14)));

		JLabel titre = new JLabel("Demande envoyée !");
		titre.setAlignmentX(Component.CENTER_ALIGNMENT);
		titre.setFont(new Font("SansSerif", Font.BOLD, 16));
		titre.setForeground(C_GREEN);
		carte.add(titre);
		carte.add(Box.createRigidArea(new Dimension(0, 10)));

		JLabel msg = new JLabel("<html><div style='text-align:center;width:280px'>"
			+ "Votre compte <b>" + identifiant + "</b> est en attente de validation.<br><br>"
			+ "Un administrateur doit approuver votre demande avant que vous puissiez vous connecter."
			+ "</div></html>");
		msg.setAlignmentX(Component.CENTER_ALIGNMENT);
		msg.setForeground(C_MUTED);
		msg.setFont(new Font("SansSerif", Font.PLAIN, 12));
		carte.add(msg);
		carte.add(Box.createRigidArea(new Dimension(0, 20)));

		JButton btnFermer = new JButton("Retour à la connexion");
		btnFermer.setAlignmentX(Component.CENTER_ALIGNMENT);
		btnFermer.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
		btnFermer.setBackground(C_BLUE);
		btnFermer.setForeground(C_WHITE);
		btnFermer.setFocusPainted(false);
		btnFermer.setFont(new Font("SansSerif", Font.BOLD, 13));
		btnFermer.addActionListener(e -> dispose());
		carte.add(btnFermer);

		getContentPane().add(carte);
		revalidate();
		repaint();
	}

	// ══════════════════════════════════════════════════════════════════════
	//  HELPERS
	// ══════════════════════════════════════════════════════════════════════

	private void setStatut(String msg, boolean erreur)
	{
		lblStatut.setText(msg);
		lblStatut.setForeground(erreur ? C_RED : C_GREEN);
	}

	private JLabel champLabel(String texte)
	{
		JLabel l = new JLabel(texte);
		l.setAlignmentX(Component.LEFT_ALIGNMENT);
		l.setForeground(C_WHITE);
		l.setFont(new Font("SansSerif", Font.BOLD, 12));
		return l;
	}

	private JTextField champTexte(String defaut)
	{
		JTextField tf = new JTextField(defaut);
		tf.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
		tf.setAlignmentX(Component.LEFT_ALIGNMENT);
		tf.setBackground(new Color(40, 40, 55));
		tf.setForeground(C_WHITE);
		tf.setCaretColor(C_WHITE);
		tf.setFont(new Font("SansSerif", Font.PLAIN, 13));
		tf.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(new Color(70, 70, 100), 1),
			new EmptyBorder(4, 8, 4, 8)));
		return tf;
	}

	private JPasswordField champPassword()
	{
		JPasswordField pf = new JPasswordField();
		pf.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
		pf.setAlignmentX(Component.LEFT_ALIGNMENT);
		pf.setBackground(new Color(40, 40, 55));
		pf.setForeground(C_WHITE);
		pf.setCaretColor(C_WHITE);
		pf.setFont(new Font("SansSerif", Font.PLAIN, 13));
		pf.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(new Color(70, 70, 100), 1),
			new EmptyBorder(4, 8, 4, 8)));
		return pf;
	}

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
}