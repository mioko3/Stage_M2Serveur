package app.ihm.diagrame;

import app.IControleur;
import app.ihm.FenetrePrincipale;
import app.ihm.IhmUtils;
import java.awt.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

public class PanelDiagrame extends JPanel
{
	private IControleur       ctrl;
	private FenetrePrincipale fP;
	private PanelGantt        panelGantt;

	public PanelDiagrame(IControleur ctrl, FenetrePrincipale fP)
	{
		this.ctrl = ctrl;
		this.fP   = fP;

		setLayout(new BorderLayout());
		setBackground(IhmUtils.FOND);

		JPanel top = new JPanel(new BorderLayout());
		top.setBackground(new Color(25, 25, 30));
		top.setBorder(new EmptyBorder(10, 15, 10, 15));

		JLabel title = new JLabel("Gantt Production (Toutes sociétés)");
		title.setForeground(Color.WHITE);
		title.setFont(new Font("Segoe UI", Font.BOLD, 22));

		JButton refresh = new JButton("Actualiser");
		refresh.setFocusable(false);
		refresh.addActionListener(e -> actualiser());

		JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT));
		right.setOpaque(false);
		right.add(refresh);

		top.add(title, BorderLayout.WEST);
		top.add(right, BorderLayout.EAST);
		add(top, BorderLayout.NORTH);

		panelGantt = new PanelGantt(ctrl);

		JScrollPane scroll = new JScrollPane(panelGantt);
		scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
		scroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
		scroll.getVerticalScrollBar().setUnitIncrement(16);
		scroll.getHorizontalScrollBar().setUnitIncrement(16);
		add(scroll, BorderLayout.CENTER);

		actualiser();
	}

	public void actualiser()
	{
		panelGantt.setLots(ctrl.getLots());
	}
}