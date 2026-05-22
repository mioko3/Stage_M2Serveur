package app.ihm;

import app.IControleur;
import app.ControleurClient;
import app.ihm.diagrame.PanelGantt;
import app.ihm.ficheroute.PanelFicheRoute;
import app.ihm.gestionlot.PanelAffectation;
import app.ihm.gestionlot.PanelLots;
import app.ihm.gestionlot.PanelSocietes;
import app.ihm.map.PanelMap;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.KeyEvent;

/**
 * Fenêtre principale de l'application.
 *
 * MODIFICATION v2 :
 *   En mode CLIENT (ControleurClient), les items de menu
 *   "Charger une sauvegarde" et "Nouveaux fichiers JSON"
 *   sont désactivés (grisés) avec une info-bulle explicative.
 *   Seule la sauvegarde reste accessible.
 */
public class FenetrePrincipale extends JFrame
{
    private final IControleur ctrl;

    private PanelAffectation panelAffectation;
    private PanelFicheRoute  panelFicheRoute;
    private PanelLots        panelLots;
    private PanelSocietes    panelSocietes;
    private PanelMap         panelMap;
    private PanelGantt       panelAuto;

    // ── Constructeur ─────────────────────────────────────────────────────

    public FenetrePrincipale(IControleur ctrl)
    {
        this.ctrl = ctrl;

        setTitle("gestionNOZ — Planning Global Futura"
            + (estModeClient() ? "  [MODE CLIENT]" : ""));
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        addWindowListener(new java.awt.event.WindowAdapter()
        {
            @Override public void windowClosing(java.awt.event.WindowEvent e)
            {
                int r = JOptionPane.showConfirmDialog(FenetrePrincipale.this,
                    "Quitter l'application ?", "Confirmation",
                    JOptionPane.YES_NO_OPTION);
                if (r == JOptionPane.YES_OPTION) System.exit(0);
            }
        });

        setSize(1400, 900);
        setMinimumSize(new Dimension(1024, 600));
        setLocationRelativeTo(null);
        setJMenuBar(creerMenuBar());

        panelAffectation = new PanelAffectation(ctrl);
        panelFicheRoute  = new PanelFicheRoute (ctrl);
        panelLots        = new PanelLots       (ctrl);
        panelSocietes    = new PanelSocietes   (ctrl, this);
        panelMap         = new PanelMap        (ctrl);
        panelAuto        = new PanelGantt      (ctrl, this);

        JTabbedPane onglets = new JTabbedPane();
        onglets.setFont(new Font("SansSerif", Font.PLAIN, 13));
        onglets.addTab("⊕ Affectation",      panelAffectation);
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

        // Bandeau d'information en mode client
        if (estModeClient())
            add(construireBandeauClient(), BorderLayout.NORTH);

        panelAffectation.remplirComboSocietes();
        rafraichirTout();
        setVisible(true);
    }

    // ── Bandeau client ────────────────────────────────────────────────────

    private JPanel construireBandeauClient()
    {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));
        p.setBackground(new Color(40, 80, 140));

        JLabel ico = new JLabel("🔒");
        ico.setFont(new Font("SansSerif", Font.PLAIN, 13));

        JLabel txt = new JLabel(
            "Mode CLIENT — Le chargement et la création de semaines sont réservés au serveur.");
        txt.setFont(new Font("SansSerif", Font.PLAIN, 12));
        txt.setForeground(new Color(200, 220, 255));

        p.add(ico);
        p.add(txt);
        return p;
    }

    // ── Menu Fichier ──────────────────────────────────────────────────────

    private JMenuBar creerMenuBar()
    {
        JMenuBar bar = new JMenuBar();

        JMenu menuFichier = new JMenu("Fichier");
        menuFichier.setFont(new Font("SansSerif", Font.PLAIN, 13));

        JMenuItem itemOuvrir      = new JMenuItem("📂  Charger une sauvegarde…");
        JMenuItem itemSauvegarder = new JMenuItem("💾  Sauvegarder      Ctrl+S");
        JMenuItem itemNouveaux    = new JMenuItem("🆕  Nouveaux fichiers JSON…");

        itemOuvrir     .addActionListener(e -> ouvrirSauvegarde());
        itemSauvegarder.addActionListener(e -> sauvegarder());
        itemNouveaux   .addActionListener(e -> nouveaux());

        itemOuvrir     .setAccelerator(KeyStroke.getKeyStroke("ctrl O"));
        itemSauvegarder.setAccelerator(KeyStroke.getKeyStroke("ctrl S"));
        itemNouveaux   .setAccelerator(KeyStroke.getKeyStroke("ctrl N"));

        // ── En mode client : désactiver charger et nouveaux ───────────────
        if (estModeClient())
        {
            String tooltip = "Réservé au serveur — demandez au responsable de changer la semaine";
            itemOuvrir .setEnabled(false);
            itemOuvrir .setToolTipText(tooltip);
            itemNouveaux.setEnabled(false);
            itemNouveaux.setToolTipText(tooltip);
        }

        menuFichier.add(itemOuvrir);
        menuFichier.addSeparator();
        menuFichier.add(itemSauvegarder);
        menuFichier.addSeparator();
        menuFichier.add(itemNouveaux);

        bar.add(menuFichier);
        return bar;
    }

    // ── Actions menu ──────────────────────────────────────────────────────

    private void ouvrirSauvegarde()
    {
        // En mode client, cette méthode ne devrait pas être accessible
        // (item désactivé), mais on garde la protection au cas où.
        if (estModeClient())
        {
            JOptionPane.showMessageDialog(this,
                "⛔  Action réservée au serveur.\nDemandez au responsable de changer la semaine active.",
                "Action non autorisée", JOptionPane.WARNING_MESSAGE);
            return;
        }

        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Ouvrir une sauvegarde JSON");
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;

        String dossier = fc.getSelectedFile().getAbsolutePath();
        try
        {
            ctrl.chargerDonnees(dossier);
            this.panelAffectation.remplirComboSocietes();
            this.panelFicheRoute.remplirComboSocietes();
            JOptionPane.showMessageDialog(this,
                "Sauvegarde chargée : " + fc.getSelectedFile().getName(),
                "Chargement OK", JOptionPane.INFORMATION_MESSAGE);
        }
        catch (Exception ex)
        {
            JOptionPane.showMessageDialog(this, "Erreur : " + ex.getMessage(),
                "Erreur", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void sauvegarder()
    {
        sauvegarderSous();
    }

    private void sauvegarderSous()
    {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Copier les fichiers JSON vers…");
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        fc.setAcceptAllFileFilterUsed(false);

        if (fc.showDialog(this, "Copier") != JFileChooser.APPROVE_OPTION) return;

        String dossier = fc.getSelectedFile().getAbsolutePath();
        String numSemaine = JOptionPane.showInputDialog(
            this, "Numéro de semaine :", "Sauvegarde — semaine", JOptionPane.PLAIN_MESSAGE);

        if (numSemaine == null || numSemaine.isBlank()) return;
        numSemaine = numSemaine.trim();

        try
        {
            ctrl.sauvegarderDonnees(dossier, numSemaine);
            JOptionPane.showMessageDialog(this,
                "Fichiers copiés vers : " + fc.getSelectedFile().getName(),
                "Sauvegarde OK", JOptionPane.INFORMATION_MESSAGE);
        }
        catch (Exception ex)
        {
            JOptionPane.showMessageDialog(this, "Erreur : " + ex.getMessage(),
                "Erreur", JOptionPane.ERROR_MESSAGE);
        }
    }

    public void nouveaux()
    {
        // En mode client, cette méthode ne devrait pas être accessible
        if (estModeClient())
        {
            JOptionPane.showMessageDialog(this,
                "⛔  Action réservée au serveur.\nDemandez au responsable de charger la nouvelle semaine.",
                "Action non autorisée", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int res = JOptionPane.showConfirmDialog(this,
            "Voulez-vous vraiment réinitialiser les données ?",
            "Nouveaux fichiers", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (res != JOptionPane.YES_OPTION) return;

        ctrl.nouveaux();
        rafraichirTout();
    }

    // ── Rafraîchissement ──────────────────────────────────────────────────

    public void rafraichirTout()
    {
        if (panelAffectation != null) { panelAffectation.remplirComboSocietes(); panelAffectation.rafraichir(); }
        if (panelFicheRoute  != null) panelFicheRoute .rafraichir();
        if (panelLots        != null) panelLots       .rafraichir();
        if (panelSocietes    != null) panelSocietes   .rafraichir();
        if (panelMap         != null) panelMap        .rafraichir();
        if (panelAuto        != null) panelAuto       .rafraichir();
    }

    // ── Utilitaire ────────────────────────────────────────────────────────

    /** Retourne true si on tourne en mode client réseau. */
    private boolean estModeClient()
    {
        return ctrl instanceof ControleurClient;
    }
}