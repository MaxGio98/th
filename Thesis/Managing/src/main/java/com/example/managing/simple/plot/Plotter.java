package com.example.managing.simple.plot;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.CategoryMarker;
import org.jfree.chart.plot.Marker;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.ValueMarker;
import org.jfree.chart.ui.ApplicationFrame;
import org.jfree.chart.ui.RectangleAnchor;
import org.jfree.chart.ui.TextAnchor;
import org.jfree.data.category.CategoryDataset;
import org.jfree.data.category.DefaultCategoryDataset;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

public class Plotter extends ApplicationFrame {

	private static final long serialVersionUID = 1L;
	private ChartPanel chartPanel;
	private JFreeChart lineChart;
	private int intervalDuration = 60;


	public Plotter(String applicationTitle, String chartTitle) {
		super(applicationTitle);

		lineChart = ChartFactory.createLineChart(chartTitle, "Time", "Percentage of heal",
				createDataset(new double[1], 0, 1), PlotOrientation.VERTICAL, true, true, false);

		chartPanel = new ChartPanel(lineChart);
		chartPanel.setPreferredSize(new java.awt.Dimension(1366, 768));
		setContentPane(chartPanel);
	}

	public Plotter(String applicationTitle, String chartTitle, int intervalDuration) {
		super(applicationTitle);

		lineChart = ChartFactory.createLineChart(chartTitle, "Time", "Percentage of heal",
				createDataset(new double[1], 0, 1), PlotOrientation.VERTICAL, true, true, false);
		this.intervalDuration = intervalDuration;
		chartPanel = new ChartPanel(lineChart);
		chartPanel.setPreferredSize(new java.awt.Dimension(1366, 768));
		setContentPane(chartPanel);
	}

	private DefaultCategoryDataset createDataset(double[] proportions, int start, int end) {
		DefaultCategoryDataset dataset = new DefaultCategoryDataset();
		double average = 0.0;
		int intervalStart = 0;

		for (int i = start; i < end; i++) {
			dataset.addValue(proportions[i], "healthy-nodes", Integer.valueOf(i));
			dataset.addValue(95, "requirement", Integer.valueOf(i));
			// Reset the interval every intervalDuration seconds
			if ((i % intervalDuration) == 0) {
				average = 0.0;
				intervalStart = i;
			}

			// Calculate the average only after 10 seconds in the interval
			if ((i % intervalDuration) >= 10) {
				average = (average * (i - intervalStart - 10) + proportions[i]) / ((i - intervalStart - 10) + 1.0);
				dataset.addValue(average, "average", Integer.valueOf(i));
			}
		}

		data = dataset;
		return dataset;
	}

	private DefaultCategoryDataset data;

	public void addValueToDataset(double[] result, int start, int end) {
		chartPanel.setChart(ChartFactory.createLineChart(lineChart.getTitle().getText(), "Time", "Percentage of heal",
				createDatasetQuick(result[end], end), PlotOrientation.VERTICAL, true, true, false));
		setContentPane(chartPanel);
		pack();
		Dimension dim = Toolkit.getDefaultToolkit().getScreenSize();
		this.setLocation(dim.width / 2 - this.getSize().width / 2, dim.height / 2 - this.getSize().height / 2);
		setVisible(true);
	}

	public void addDataset(double[] result, int start, int end) {
		chartPanel.setChart(ChartFactory.createLineChart(lineChart.getTitle().getText(), "Time", "Percentage of heal",
				createDataset(result, start, end), PlotOrientation.VERTICAL, true, true, false));
		//set color of the lines
		chartPanel.getChart().getCategoryPlot().getRenderer().setSeriesPaint(0, Color.RED);
		chartPanel.getChart().getCategoryPlot().getRenderer().setSeriesPaint(1, Color.GREEN);
		chartPanel.getChart().getCategoryPlot().getRenderer().setSeriesPaint(2, Color.BLUE);
		//set background color to white
		chartPanel.getChart().getPlot().setBackgroundPaint(Color.WHITE);
		//set thicker lines
		chartPanel.getChart().getCategoryPlot().getRenderer().setSeriesStroke(0, new BasicStroke(2.0f));
		chartPanel.getChart().getCategoryPlot().getRenderer().setSeriesStroke(1, new BasicStroke(2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1.0f, new float[]{10.0f, 6.0f}, 0.0f));
		chartPanel.getChart().getCategoryPlot().getRenderer().setSeriesStroke(2, new BasicStroke(2.0f));
		setContentPane(chartPanel);
		pack();
		Dimension dim = Toolkit.getDefaultToolkit().getScreenSize();
		this.setLocation(dim.width / 2 - this.getSize().width / 2, dim.height / 2 - this.getSize().height / 2);
	}

	private CategoryDataset createDatasetQuick(double d, int position) {
		data.addValue(d, "healthy-nodes", Integer.valueOf(position));
		double average = 0.0;
		int intervalStart = position - (position % intervalDuration);
		data.addValue(95, "requirement", Integer.valueOf(position));

		if ((position % intervalDuration) >= 10) {
			for (int i = intervalStart; i <= position; i++) {
				average = (average * (i - intervalStart - 10) + (double) data.getValue("healthy-nodes", Integer.valueOf(i))) / ((i - intervalStart - 10) + 1.0);
			}
			data.addValue(average, "average", Integer.valueOf(position));
		}

		return data;
	}

	public void save(String filename) throws IOException {
		Rectangle rec = chartPanel.getBounds();
		BufferedImage img = new BufferedImage(rec.width, rec.height, BufferedImage.TYPE_INT_ARGB);
		Graphics g = img.getGraphics();
		chartPanel.paint(g);
		File filepng = new File("target/" + filename + ".png");
		ImageIO.write(img, "png", filepng);
	}

	public BufferedImage getBufferedImage() {
		BufferedImage img = new BufferedImage(1366, 768, BufferedImage.TYPE_INT_RGB);
		Graphics g = img.getGraphics();
		chartPanel.paint(g);
		return img;
	}

	public static void PlotResults(double[] results) {
		(new Plotter("Network simulation", "Proportion of healthy nodes")).addDataset(results, 0, results.length);
	}
}