package app.ihm.login;

import app.Controleur;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

/**
 * Fenêtre de connexion — mode SOLO uniquement.
 * Prend un Controleur (pas IControleur) car elle appelle lancerApp()
 * qui est propre au mode solo.
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

		carte.add(label("GLOBAL FUTURA", new Font("SansSerif", Font.BOLD, 28), new Color(120, 170, 255)));
		carte.add(Box.createRigidArea(new Dimension(0, 6)));
		carte.add(label("Connexion au planning", new Font("SansSerif", Font.PLAIN, 15), new Color(180, 180, 190)));
		carte.add(Box.createRigidArea(new Dimension(0, 30)));
		carte.add(label("IDENTIFIANT", new Font("SansSerif", Font.BOLD, 13), Color.WHITE));
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

		carte.add(label("SOURCE DES DONNÉES", new Font("SansSerif", Font.BOLD, 13), new Color(190, 190, 200)));
		carte.add(Box.createRigidArea(new Dimension(0, 12)));

		chkFichierCourant = checkbox("Utiliser les fichiers courants (JSON)");
		chkExport         = checkbox("Faire une nouvelle semaine (Excel)");
		chkSave           = checkbox("Utiliser une semaine sauvegardée (JSON)");
		chkFichierCourant.setSelected(true);

		ButtonGroup grp = new ButtonGroup();
		grp.add(chkFichierCourant);
		grp.add(chkExport);
		grp.add(chkSave);

		carte.add(chkFichierCourant);
		carte.add(Box.createRigidArea(new Dimension(0, 8)));
		carte.add(chkExport);
		carte.add(Box.createRigidArea(new Dimension(0, 8)));
		carte.add(chkSave);

		fond.add(carte);
		return fond;
	}

	@Override
	public void actionPerformed(ActionEvent e)
	{
		tenterConnexion();
	}

	private void tenterConnexion()
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

		if (chkSave.isSelected())
		{
			ouvrirSauvegarde();
		}
		else
		{
			ctrl.lancerApp(saisie, chkExport.isSelected());
		}
	}

	private void ouvrirSauvegarde()
	{
		JFileChooser fc = new JFileChooser();
		fc.setDialogTitle("Ouvrir une sauvegarde JSON");
		fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		if (fc.showOpenDialog(null) == JFileChooser.APPROVE_OPTION)
		{
			String dossier = fc.getSelectedFile().getAbsolutePath();
			// Lance l'app avec JSON courant puis charge la sauvegarde
			ctrl.lancerApp("", false);
			try { ctrl.chargerDonnees(dossier); }
			catch (Exception ex) { JOptionPane.showMessageDialog(null, "Erreur : " + ex.getMessage(), "Erreur", JOptionPane.ERROR_MESSAGE); }
		}
		else
		{
			ctrl.lancerApp("", false);
		}
	}

	private boolean loginValide(String log)
	{ for (String l : LOGIN_VALIDES) if (l.equals(log)) return true; return false; }

	private JLabel label(String texte, Font font, Color couleur)
	{
		JLabel l = new JLabel(texte);
		l.setAlignmentX(Component.CENTER_ALIGNMENT);
		l.setFont(font); l.setForeground(couleur);
		return l;
	}

	private JButton bouton(String texte, Color couleur)
	{
		JButton b = new JButton(texte);
		b.setAlignmentX(Component.CENTER_ALIGNMENT);
		b.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));
		b.setFocusPainted(false); b.setCursor(new Cursor(Cursor.HAND_CURSOR));
		b.setBackground(couleur); b.setForeground(Color.WHITE);
		b.setFont(new Font("SansSerif", Font.BOLD, 14));
		b.setBorder(new EmptyBorder(12, 20, 12, 20));
		return b;
	}

	private JCheckBox checkbox(String texte)
	{
		JCheckBox c = new JCheckBox(texte);
		c.setAlignmentX(Component.CENTER_ALIGNMENT);
		c.setBackground(new Color(28, 28, 40)); c.setForeground(Color.WHITE);
		c.setFocusPainted(false); c.setFont(new Font("SansSerif", Font.PLAIN, 13));
		c.setCursor(new Cursor(Cursor.HAND_CURSOR));
		return c;
	}
}
