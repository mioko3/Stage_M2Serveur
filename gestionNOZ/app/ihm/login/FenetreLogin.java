package app.ihm.login;

import app.Controleur;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

/**
 * Fenêtre de connexion — mode standalone uniquement.
 *
 * Elle s'affiche AVANT le chargement des données.
 * Lorsque l'identifiant est valide, elle appelle ctrl.lancerApp(login, utiliserExcel).
 *
 * Note : utilise Controleur (pas IControleur) car lancerApp() est
 * spécifique au mode standalone. Le mode client (ControleurClient)
 * n'utilise pas FenetreLogin — il se connecte directement au serveur.
 */
public class FenetreLogin extends JFrame implements ActionListener
{
	private static final String[] LOGIN_VALIDES = { "PAM" };

	private final Controleur ctrl;

	private JTextField txtLogin;
	private JButton    btnConnexion;
	private JCheckBox  chkFichierCourant;
	private JCheckBox  chkExport;
	private JCheckBox  chkSave;
	private JLabel     lblErreur;

	public FenetreLogin(Controleur ctrl)
	{
		this.ctrl = ctrl;

		setTitle("Planning Global Futura — Connexion");
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setSize(480, 550);
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
		carte.setPreferredSize(new Dimension(400, 490));
		carte.setBackground(new Color(28, 28, 40));
		carte.setLayout(new BoxLayout(carte, BoxLayout.Y_AXIS));
		carte.setBorder(new EmptyBorder(35, 40, 35, 40));

		carte.add(labelCentre("GLOBAL FUTURA",
			new Font("SansSerif", Font.BOLD, 28), new Color(120, 170, 255)));
		carte.add(Box.createRigidArea(new Dimension(0, 6)));
		carte.add(labelCentre("Connexion au planning",
			new Font("SansSerif", Font.PLAIN, 15), new Color(180, 180, 190)));
		carte.add(Box.createRigidArea(new Dimension(0, 30)));

		carte.add(labelCentre("IDENTIFIANT",
			new Font("SansSerif", Font.BOLD, 13), Color.WHITE));
		carte.add(Box.createRigidArea(new Dimension(0, 10)));

		txtLogin = new JTextField();
		txtLogin.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
		txtLogin.setBackground(new Color(40, 40, 55));
		txtLogin.setForeground(Color.WHITE);
		txtLogin.setCaretColor(Color.WHITE);
		txtLogin.setHorizontalAlignment(JTextField.CENTER);
		txtLogin.setFont(new Font("SansSerif", Font.PLAIN, 17));
		txtLogin.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(new Color(70, 70, 100), 2),
			new EmptyBorder(8, 15, 8, 15)));
		txtLogin.addActionListener(this);
		carte.add(txtLogin);
		carte.add(Box.createRigidArea(new Dimension(0, 8)));

		lblErreur = new JLabel(" ");
		lblErreur.setAlignmentX(Component.CENTER_ALIGNMENT);
		lblErreur.setForeground(new Color(255, 90, 90));
		lblErreur.setFont(new Font("SansSerif", Font.ITALIC, 12));
		carte.add(lblErreur);
		carte.add(Box.createRigidArea(new Dimension(0, 14)));

		btnConnexion = bouton("SE CONNECTER", new Color(70, 120, 255));
		btnConnexion.addActionListener(this);
		carte.add(btnConnexion);
		carte.add(Box.createRigidArea(new Dimension(0, 28)));

		JSeparator sep = new JSeparator();
		sep.setForeground(new Color(60, 60, 80));
		sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
		carte.add(sep);
		carte.add(Box.createRigidArea(new Dimension(0, 20)));

		carte.add(labelCentre("SOURCE DES DONNÉES",
			new Font("SansSerif", Font.BOLD, 11), new Color(130, 130, 150)));
		carte.add(Box.createRigidArea(new Dimension(0, 10)));

		chkFichierCourant = checkbox("Utiliser le fichier courant (JSON)");
		chkExport         = checkbox("Importer depuis Excel (XLSX)");
		chkSave           = checkbox("Charger une sauvegarde…");

		ButtonGroup group = new ButtonGroup();
		group.add(chkFichierCourant);
		group.add(chkExport);
		group.add(chkSave);
		chkFichierCourant.setSelected(true);

		carte.add(chkFichierCourant);
		carte.add(Box.createRigidArea(new Dimension(0, 4)));
		carte.add(chkExport);
		carte.add(Box.createRigidArea(new Dimension(0, 4)));
		carte.add(chkSave);

		fond.add(carte);
		return fond;
	}

	@Override
	public void actionPerformed(ActionEvent e)
	{
		String saisie = txtLogin.getText().trim().toUpperCase();

		if (!loginValide(saisie))
		{
			lblErreur.setText("Identifiant inconnu : " + saisie);
			txtLogin.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(new Color(200, 60, 60), 2),
				new EmptyBorder(8, 15, 8, 15)));
			return;
		}

		setVisible(false);
		dispose();

		boolean utiliserExcel = chkExport.isSelected();
		ctrl.lancerApp(saisie, utiliserExcel);

		if (chkSave.isSelected())
			ouvrirSauvegarde();
	}

	private void ouvrirSauvegarde()
	{
		JFileChooser fc = new JFileChooser();
		fc.setDialogTitle("Charger une sauvegarde JSON");
		fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		if (fc.showOpenDialog(null) == JFileChooser.APPROVE_OPTION)
		{
			try { ctrl.chargerDonnees(fc.getSelectedFile().getAbsolutePath()); }
			catch (Exception ex)
			{
				JOptionPane.showMessageDialog(null,
					"Erreur chargement : " + ex.getMessage(),
					"Erreur", JOptionPane.ERROR_MESSAGE);
			}
		}
	}

	private boolean loginValide(String log)
	{
		for (String l : LOGIN_VALIDES)
			if (l.equals(log)) return true;
		return false;
	}

	private JLabel labelCentre(String texte, Font font, Color couleur)
	{
		JLabel l = new JLabel(texte);
		l.setAlignmentX(Component.CENTER_ALIGNMENT);
		l.setFont(font);
		l.setForeground(couleur);
		return l;
	}

	private JButton bouton(String texte, Color couleur)
	{
		JButton b = new JButton(texte);
		b.setAlignmentX(Component.CENTER_ALIGNMENT);
		b.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));
		b.setFocusPainted(false);
		b.setCursor(new Cursor(Cursor.HAND_CURSOR));
		b.setBackground(couleur);
		b.setForeground(Color.WHITE);
		b.setFont(new Font("SansSerif", Font.BOLD, 14));
		b.setBorder(new EmptyBorder(12, 20, 12, 20));
		return b;
	}

	private JCheckBox checkbox(String texte)
	{
		JCheckBox c = new JCheckBox(texte);
		c.setAlignmentX(Component.CENTER_ALIGNMENT);
		c.setBackground(new Color(28, 28, 40));
		c.setForeground(Color.WHITE);
		c.setFocusPainted(false);
		c.setFont(new Font("SansSerif", Font.PLAIN, 13));
		c.setCursor(new Cursor(Cursor.HAND_CURSOR));
		return c;
	}
}
