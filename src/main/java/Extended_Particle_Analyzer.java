

import java.util.*;
import java.awt.Color;
import ij.*;
import ij.measure.*;
import ij.plugin.frame.RoiManager;
import ij.plugin.filter.*;
import ij.process.*;
import ij.gui.GenericDialog;
import ij.gui.Wand;
import ij.gui.Roi;
import ij.gui.PolygonRoi;
import ij.plugin.CanvasResizer;
import ij.text.TextWindow;
import ij.plugin.frame.Recorder;

/*
 *	Copyright (C), Jan Brocher / BioVoxxel. All rights reserved.
 *
 *	All Macros/Plugins were written by Jan Brocher/BioVoxxel.
 *
 *	Redistribution and use in source and binary forms of all plugins and macros, with or without modification, 
 *	are permitted provided that the following conditions are met:
 *
 *	1.) Redistributions of source code must retain the above copyright notice, 
 *	this list of conditions and the following disclaimer.
 *	2.) Redistributions in binary form must reproduce the above copyright notice, this list of conditions 
 *	and the following disclaimer in the documentation and/or other materials provided with the distribution.
 *  3.) Neither the name of BioVoxxel nor the names of its contributors may be used to endorse or promote 
 *  products derived from this software without specific prior written permission.
 *	
 *	DISCLAIMER:
 *
 *	THIS SOFTWARE IS PROVIDED BY THE REGENTS AND CONTRIBUTORS ?AS IS? AND ANY EXPRESS OR IMPLIED WARRANTIES, 
 *	INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 *	DISCLAIMED. IN NO EVENT SHALL THE REGENTS OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, 
 *	EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 *	SERVICES;  LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, 
 *	WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE 
 *	USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 */

/**
 	Copyright (C), Jan Brocher / BioVoxxel.

	All Macros/Plugins were written by Jan Brocher/BioVoxxel.

	Redistribution and use in source and binary forms of all plugins and macros, with or without modification, are permitted provided that the following conditions are met:

	1.) Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
	2.) Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the documentation and/or other materials provided with the distribution.
	
	DISCLAIMER:

	THIS SOFTWARE IS PROVIDED BY THE REGENTS AND CONTRIBUTORS ?AS IS? AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE REGENTS OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 
	@author Jan Brocher/BioVoxxel
	@version 0.2.5
 
	version history:
	0.2.5: improved exception handling and parameter control / redirecting to original image correctly analyzes Mean, Skew and Kurtosis
	0.2.6: measurements from a redirected intensity based images are correctly placed in the results table

 */

public class Extended_Particle_Analyzer implements PlugInFilter {

	ImagePlus imp1, redirectedImg, outputImg;
	//ImageProcessor ip2;
	private int resultsCounter = 0;
	//ResultsTable finalResultsTable = new ResultsTable();
	private RoiManager redirectRoiManager = new RoiManager(true);
	private int nPasses, pass;
	private int flags = DOES_8G;
	
	//variable for image titles
	private int originalImgID;
	private String originalImgTitle;
	private int outputImgID;
	private String outputImgTitle;
	
	//image dimension variables
	private int width, height, slices;
	private int[] imgDimensions = new int[5];
	
	//calibration variables
	private double pixelWidth, pixelHeight, squaredPixel;

		
	//Dialog and restriction parameter definition
	private String Area, Extent, Perimeter, Circularity, Roundness, Solidity, Compactness, AR, FeretAR, EllipsoidAngle, MaxFeret, FeretAngle, COV;
	private String Output, Redirect, Correction;
	private String unit;
	private String reset;
	private boolean usePixel, Reset, DisplayResults, ClearResults, Summarize, AddToManager, ExcludeEdges, IncludeHoles; //checkbox variable
	private int displayResults, summarize, addtoManager, excludeEdges, includeHoles; //checkbox result variables
	private int currentPAOptions = ParticleAnalyzer.CLEAR_WORKSHEET|ParticleAnalyzer.RECORD_STARTS|ParticleAnalyzer.SHOW_MASKS;
	private int measurementFlags = Measurements.AREA|Measurements.MEAN|Measurements.STD_DEV|Measurements.MODE|Measurements.MIN_MAX|Measurements.CENTROID|Measurements.CENTER_OF_MASS|Measurements.PERIMETER|Measurements.RECT|Measurements.ELLIPSE|Measurements.SHAPE_DESCRIPTORS|Measurements.FERET|Measurements.INTEGRATED_DENSITY|Measurements.MEDIAN|Measurements.SKEWNESS|Measurements.KURTOSIS|Measurements.AREA_FRACTION|Measurements.STACK_POSITION|Measurements.LIMIT|Measurements.LABELS;
	private int outputOptions = ParticleAnalyzer.RECORD_STARTS;
	private double AreaMin = 0.0;
	private double AreaMax = 999999999.9;
	private double CircularityMin = 0.0;
	private double CircularityMax = 1.0;
		
	//measurement variables
	private int[] X, Y;
	private int[] keptResults;

	
	//------------------------------------------------------------------------------------------------------------------------	
	public int setup(String arg, ImagePlus imp1) {
    		this.imp1 = imp1;
    		return flags;
	}
	
	public void run(ImageProcessor ip1) {


	//------------------------------------------------------------------------------------------------------------------------

		if(!imp1.getProcessor().isBinary()) {
			IJ.error("works with 8-bit binary images only");
			return;
		}

		//getting general dimensional information
		originalImgID = imp1.getID();
		originalImgTitle = imp1.getTitle();
			//IJ.log(""+originalImgID);
		width = imp1.getWidth();
		height = imp1.getHeight();
		imgDimensions = imp1.getDimensions();
		slices = imgDimensions[3];
		if(slices>1) {
			IJ.error("does not work with stacks");
			return;
		}

		if(!Prefs.blackBackground) {
			ip1.invert();
		}
		if(Prefs.blackBackground && ip1.isInvertedLut()) {
			ip1.invertLut();
		}

		//reading in calibration information
		Calibration calibImg = imp1.getCalibration();
		
		unit = calibImg.getUnit();
		pixelWidth = calibImg.pixelWidth;
		pixelHeight = calibImg.pixelHeight;
		squaredPixel = pixelWidth * pixelHeight;
		
		//prepare invironment and reas in names of all open image windows
		String[] imageNames = getImageNames(imp1);
		
		//define variables
		String previousArea = Prefs.get("advPartAnal.area", "0-Infinity");
		String previousExtent = Prefs.get("advPartAnal.Extent", "0.00-1.00");
		String previousPerim = Prefs.get("advPartAnal.perimeter", "0-Infinity");
		String previousCirc = Prefs.get("advPartAnal.circularity", "0.00-1.00");
		String previousRound = Prefs.get("advPartAnal.roundness", "0.00-1.00");
		String previousSolidity = Prefs.get("advPartAnal.solidity", "0.00-1.00");
		String previousCompactness = Prefs.get("advPartAnal.compactness", "0.00-1.00");
		String previousAR = Prefs.get("advPartAnal.AR", "0.00-Infinity");
		String previousFAR = Prefs.get("advPartAnal.FAR", "0.00-Infinity");
		String previousAngle = Prefs.get("advPartAnal.angle", "0-180");
		String previousMaxFeret = Prefs.get("advPartAnal.max.feret", "0-Infinity");
		String previousFeretAngle = Prefs.get("advPartAnal.feret.angle", "0-180");
		String previousCOV = Prefs.get("advPartAnal.Stringiation.coefficient", "0.00-1.00");
		String previousShow = Prefs.get("advPartAnal.show", "Masks");
		String previousCorrection = Prefs.get("advPartAnal.borderCountCorrection", "None");
		String[] checkboxLabels = new String[] {"Display results", "Clear results", "Summarize", "Add to Manager", "Exclude edges", "Include holes", "Reset after analysis"};
		boolean[] previousCheckboxGroup = new boolean[7];
		previousCheckboxGroup[0] = Prefs.get("advPartAnal.CB0", true);
		previousCheckboxGroup[1] = Prefs.get("advPartAnal.CB1", false);
		previousCheckboxGroup[2] = Prefs.get("advPartAnal.CB2", false);
		previousCheckboxGroup[3] = Prefs.get("advPartAnal.CB3", false);
		previousCheckboxGroup[4] = Prefs.get("advPartAnal.CB4", false);
		previousCheckboxGroup[5] = Prefs.get("advPartAnal.CB5", false);
		previousCheckboxGroup[6] = Prefs.get("advPartAnal.CB6", false);
		
		//Setup including shape descriptors
		GenericDialog APAdialog = new GenericDialog("Extended Particle Analyzer");
		
			APAdialog.addStringField("Area ("+unit+"^2)", previousArea);
			if(!unit.equals("pixels") && !unit.equals("pixel")) {
				APAdialog.addCheckbox("Pixel units", true);
			}
			APAdialog.addStringField("Extent", previousExtent);
			APAdialog.addStringField("Perimeter (pixel)", previousPerim);
			APAdialog.addStringField("Circularity", previousCirc);
			APAdialog.addStringField("Roundness (IJ)", previousRound);
			APAdialog.addStringField("Solidity", previousSolidity);
			APAdialog.addStringField("Compactness", previousCompactness);
			APAdialog.addStringField("Aspect ratio (AR)", previousAR);
			APAdialog.addStringField("Feret_AR", previousFAR);
			APAdialog.addStringField("Ellipsoid_angle (degree)", previousAngle);
			APAdialog.addStringField("Max_Feret", previousMaxFeret);
			APAdialog.addStringField("Feret_Angle (degree)", previousFeretAngle);
			APAdialog.addStringField("Coefficient of variation", previousCOV);
			APAdialog.addChoice("Show", new String[] {"Nothing", "Masks", "Outlines", "Count Masks", "Overlay Outlines", "Overlay Masks"}, previousShow);
			APAdialog.addChoice("Redirect to", imageNames, "None");
			APAdialog.addChoice("Keep borders (correction)", new String[] {"None", "Top-Left", "Top-Right", "Bottom-Left", "Bottom-Right"}, previousCorrection);
			APAdialog.addCheckboxGroup(4, 2, checkboxLabels, previousCheckboxGroup);
			APAdialog.addHelp("http://fiji.sc/BioVoxxel_Toolbox");
			APAdialog.showDialog();
			APAdialog.setSmartRecording(true);
			if(APAdialog.wasCanceled()) {
				return;
			}
			Area=APAdialog.getNextString();
			testValidUserInput(Area);
			Prefs.set("advPartAnal.area", Area);
			
			if(!unit.equals("pixels") && !unit.equals("pixel")) {
				usePixel=APAdialog.getNextBoolean();
				Prefs.set("advPartAnal.unit", usePixel);
			}
			
			Extent=APAdialog.getNextString();
			testValidUserInput(Extent);
			testValidShapeDescriptor(Extent);
			Prefs.set("advPartAnal.Extent", Extent);
			
			Perimeter=APAdialog.getNextString();
			testValidUserInput(Perimeter);
			Prefs.set("advPartAnal.perimeter", Perimeter);
			
			Circularity=APAdialog.getNextString();
			testValidUserInput(Circularity);
			testValidShapeDescriptor(Circularity);
			Prefs.set("advPartAnal.circularity", Circularity);
			
			Roundness=APAdialog.getNextString();
			testValidUserInput(Roundness);
			testValidShapeDescriptor(Roundness);
			Prefs.set("advPartAnal.roundness", Roundness);
			
			Solidity=APAdialog.getNextString();
			testValidUserInput(Solidity);
			testValidShapeDescriptor(Solidity);
			Prefs.set("advPartAnal.solidity", Solidity);
			
			Compactness=APAdialog.getNextString();
			testValidUserInput(Compactness);
			testValidShapeDescriptor(Compactness);
			Prefs.set("advPartAnal.compactness", Compactness);
			
			AR=APAdialog.getNextString();
			testValidUserInput(AR);
			Prefs.set("advPartAnal.AR", AR);
			
			FeretAR=APAdialog.getNextString();
			testValidUserInput(FeretAR);
			Prefs.set("advPartAnal.FAR", FeretAR);
			
			EllipsoidAngle=APAdialog.getNextString();
			testValidUserInput(EllipsoidAngle);
			testValidAngle(EllipsoidAngle);
			Prefs.set("advPartAnal.angle", EllipsoidAngle);
			
			MaxFeret=APAdialog.getNextString();
			testValidUserInput(MaxFeret);
			Prefs.set("advPartAnal.max.feret", MaxFeret);
			
			FeretAngle=APAdialog.getNextString();
			testValidUserInput(FeretAngle);
			testValidAngle(FeretAngle);
			Prefs.set("advPartAnal.feret.angle", FeretAngle);
			
			COV=APAdialog.getNextString();
			testValidUserInput(COV);
			Prefs.set("advPartAnal.variation.coefficient", COV);	
			
			Output=APAdialog.getNextChoice();
			Prefs.set("advPartAnal.show", Output);
						
			Redirect=APAdialog.getNextChoice();
			
			Correction=APAdialog.getNextChoice();
			Prefs.set("advPartAnal.borderCountCorrection", Correction);
						
			DisplayResults=APAdialog.getNextBoolean();
			Prefs.set("advPartAnal.CB0", DisplayResults);

			ClearResults=APAdialog.getNextBoolean();
			Prefs.set("advPartAnal.CB1", ClearResults);
			
			Summarize=APAdialog.getNextBoolean();
			Prefs.set("advPartAnal.CB2", Summarize);
			
			AddToManager=APAdialog.getNextBoolean();
			Prefs.set("advPartAnal.CB3", AddToManager);
			
			ExcludeEdges=APAdialog.getNextBoolean();
			Prefs.set("advPartAnal.CB4", ExcludeEdges);
						
			IncludeHoles=APAdialog.getNextBoolean();
			Prefs.set("advPartAnal.CB5", IncludeHoles);
			
			Reset=APAdialog.getNextBoolean();
			Prefs.set("advPartAnal.CB6", Reset);

	//------------------------------------------------------------------------------------------------------------------------

		
	    if(!ip1.isBinary()) {
			IJ.showMessage("works only on individual 8-bit binary images");
			return;
		}

		defineParticleAnalyzers();
		
		ImageProcessor ip2 = ip1.duplicate();
		
		//-------------------------------------------------------------------------------------------
		
		ip2 = borderCountCorrection(ip1, Correction);
		ImagePlus imp2 = new ImagePlus(WindowManager.getUniqueName(originalImgTitle), ip2);		
				
		if(!Redirect.equals("None")) {
			currentPAOptions |= ParticleAnalyzer.ADD_TO_MANAGER;
			ParticleAnalyzer.setRoiManager(redirectRoiManager);
			IJ.selectWindow(Redirect);
			redirectedImg = IJ.getImage();	
		}
		
		
		//-------------------------------------------------------------------------------------------
	     
	     particleAnalysis(ip2, imp2, originalImgTitle);
	     if(IJ.escapePressed()) {
			ip1.reset();
	     }

		if(!Prefs.blackBackground) {
		ip1.invert();
		}
		if(Prefs.blackBackground && ip1.isInvertedLut()) {
			ip1.invertLut();
		}
	}
	
	//------------------------------------------------------------------------------------------------------------------------

	public void particleAnalysis(ImageProcessor ip, ImagePlus imp2, String originalImageTitle) {

		if(!Area.equals("0-Infinity") || !Area.equals("0-infinity")) {
			AreaMin = Double.parseDouble(Area.substring(0, Area.indexOf("-")));
			String AreaInterMax = Area.substring(Area.indexOf("-")+1);
			if(AreaInterMax.equals("Infinity") || AreaInterMax.equals("infinity")) { 
				AreaMax = 999999999.9;
			} else {
				AreaMax = Double.parseDouble(Area.substring(Area.indexOf("-")+1));
			}
		} 

		if(!unit.equals("pixels") || !unit.equals("pixel")) {
			if(usePixel) {
				AreaMin = AreaMin / squaredPixel;
				AreaMax = AreaMax / squaredPixel;
			}
		}

		if(!Circularity.equals("0.00-1.00")) {
			CircularityMin = Double.parseDouble(Circularity.substring(0, Circularity.indexOf("-")));
			CircularityMax = Double.parseDouble(Circularity.substring(Circularity.indexOf("-")+1));
		}
		
		//makes sure that renaming of results tables is not recorded
		Recorder rec = Recorder.getInstance();
		if(rec!=null) {
			 Recorder.record = false;
		}
		//read in existing results table	
		TextWindow existingResultsTableWindow = ResultsTable.getResultsWindow();
		if(existingResultsTableWindow!=null) {
			IJ.renameResults("oldResultsTable");
		}
		ResultsTable initialResultsTable = new ResultsTable();
		
		//define the new particle analyzer
		ParticleAnalyzer initialPA = new ParticleAnalyzer(currentPAOptions, measurementFlags, initialResultsTable, AreaMin, AreaMax, CircularityMin, CircularityMax);
		initialPA.setHideOutputImage(true);
		
		//perform the initial analysis of the image 
		initialPA.analyze(imp2);
		int initialResultNumber = initialResultsTable.getCounter();
			//resultsTable.show("Results"); //keep for test output
			//IJ.renameResults("Results", "initial Results Table");
		
		X = new int[initialResultNumber];
		Y = new int[initialResultNumber];
		keptResults = new int[initialResultNumber];
		
		for(int coord=0; coord<initialResultNumber; coord++) {
			X[coord] = (int) initialResultsTable.getValue("XStart", coord);
			Y[coord] = (int) initialResultsTable.getValue("YStart", coord);
		}
		
		ImagePlus tempImg = initialPA.getOutputImage();
		ImageProcessor tempIP = tempImg.getProcessor();
		if(tempIP.isInvertedLut()) {
			tempIP.invertLut();
		}
			//tempImg.updateAndDraw(); //not necessary to display this intermediate image but keep as control output option

		//Read in ROIs and values from the redirected image to be able to analyze coefficient of variance, skewness and kurtosis
		
		if(!Redirect.equals("None")) {
			int roiNumber = redirectRoiManager.getCount();
			IJ.selectWindow(Redirect);
			initialResultsTable.reset();
			redirectRoiManager.runCommand("Measure");
			initialResultsTable = Analyzer.getResultsTable();
				//redirectedResultsTable.show("Results");
				//IJ.renameResults("ROIs");
		}
				
		//Calculate additional values not present in original results table from the normal particle analyzer
		double[] compactness = new double[initialResultNumber];
		double[] FAR = new double[initialResultNumber];
		double[] extent = new double[initialResultNumber];
		double[] cov = new double[initialResultNumber];
		double[] originalMeanValue = new double[initialResultNumber];
		double[] originalMedian = new double[initialResultNumber];
		double[] originalMode = new double[initialResultNumber];
		double[] originalStdDev = new double[initialResultNumber];
		double[] originalIntDen = new double[initialResultNumber];
		double[] originalRawIntDen = new double[initialResultNumber];
		double[] originalMin = new double[initialResultNumber];
		double[] originalMax = new double[initialResultNumber];
		double[] originalSkewness = new double[initialResultNumber];
		double[] originalKurtosis = new double[initialResultNumber];
		for(int calc=0; calc<initialResultNumber; calc++) {
			originalMeanValue[calc] = initialResultsTable.getValue("Mean", calc);
			originalMedian[calc] = initialResultsTable.getValue("Median", calc);
			originalMode[calc] = initialResultsTable.getValue("Mode", calc);
			originalStdDev[calc] = initialResultsTable.getValue("StdDev", calc);
			originalIntDen[calc] = initialResultsTable.getValue("IntDen", calc);
			originalRawIntDen[calc] = initialResultsTable.getValue("RawIntDen", calc);
			originalMin[calc] = initialResultsTable.getValue("Min", calc);
			originalMax[calc] = initialResultsTable.getValue("Max", calc);
			originalSkewness[calc] = initialResultsTable.getValue("Skew", calc);
			originalKurtosis[calc] = initialResultsTable.getValue("Kurt", calc);
			FAR[calc]=((initialResultsTable.getValue("Feret", calc))/(initialResultsTable.getValue("MinFeret", calc)));
			compactness[calc]=(Math.sqrt((4/Math.PI)*initialResultsTable.getValue("Area", calc))/initialResultsTable.getValue("Major", calc));
			extent[calc]=(initialResultsTable.getValue("Area", calc)/((initialResultsTable.getValue("Width", calc))*(initialResultsTable.getValue("Height", calc))));
			cov[calc]=((initialResultsTable.getValue("StdDev", calc))/(initialResultsTable.getValue("Mean", calc)));
		}
	
		//elimination process of particles
		FloodFiller filledImage = new FloodFiller(tempIP);
		tempIP.setValue(0.0);

		Boolean continueProcessing;
		int KeptResultsCount = 0;
		for(int n=0; n<initialResultNumber; n++) {
			continueProcessing=true;
			if(!Extent.equals("0.00-1.00") && continueProcessing) {
				double ExtentMin = Double.parseDouble(Extent.substring(0, Extent.indexOf("-")));
				double ExtentMax = Double.parseDouble(Extent.substring(Extent.indexOf("-")+1));
				if(extent[n]<ExtentMin || extent[n]>ExtentMax) {
					filledImage.fill8(X[n], Y[n]);
					continueProcessing=true;
					//IJ.log("Extent");
				}
			}
			
			
			if((!Perimeter.equals("0-Infinity") || !Perimeter.equals("0-infinity")) && continueProcessing) {
					double PerimeterMin = Double.parseDouble(Perimeter.substring(0, Perimeter.indexOf("-")));
					double PerimeterMax = 999999999.9;
					String PerimeterInterMax = Perimeter.substring(Perimeter.indexOf("-")+1);
					if(PerimeterInterMax.equals("Infinity") || PerimeterInterMax.equals("infinity")) { 
						PerimeterMax = 999999999.9;
					} else {
						PerimeterMax = Double.parseDouble(Perimeter.substring(Perimeter.indexOf("-")+1));
					}
					double currentPerimeter = initialResultsTable.getValue("Perim.", n);
					if(!unit.equals("pixels") || !unit.equals("pixel")) {
						if(usePixel) {
							currentPerimeter = currentPerimeter / pixelWidth;
						}
					}
					if(currentPerimeter<PerimeterMin || currentPerimeter>PerimeterMax) {
						filledImage.fill8(X[n], Y[n]);
						continueProcessing=true;
						//IJ.log("Perimeter");
					}
						
			}
	
			if(!Roundness.equals("0.00-1.00") && continueProcessing) {
					double RoundnessMin = Double.parseDouble(Roundness.substring(0, Roundness.indexOf("-")));
					double RoundnessMax = Double.parseDouble(Roundness.substring(Roundness.indexOf("-")+1));
					if(initialResultsTable.getValue("Round", n)<RoundnessMin || initialResultsTable.getValue("Round", n)>RoundnessMax) {
						filledImage.fill8(X[n], Y[n]);
						continueProcessing=true;
						//IJ.log("Roundness");
					}
			}
				
			if(!Solidity.equals("0.00-1.00") && continueProcessing) {
					double SolidityMin = Double.parseDouble(Solidity.substring(0, Solidity.indexOf("-")));
					double SolidityMax = Double.parseDouble(Solidity.substring(Solidity.indexOf("-")+1));
					if(initialResultsTable.getValue("Solidity", n)<SolidityMin || initialResultsTable.getValue("Solidity", n)>SolidityMax) {
						filledImage.fill8(X[n], Y[n]);
						continueProcessing=true;
						//IJ.log("Solidity");
					}
			}
	
			if(!Compactness.equals("0.00-1.00") && continueProcessing) {
					double CompactnessMin = Double.parseDouble(Compactness.substring(0, Compactness.indexOf("-")));
					double CompactnessMax = Double.parseDouble(Compactness.substring(Compactness.indexOf("-")+1));
					if(compactness[n]<CompactnessMin || compactness[n]>CompactnessMax) {
						filledImage.fill8(X[n], Y[n]);
						continueProcessing=true;
						//IJ.log("Compactness");
					}
			}
				
			if((!AR.equals("0.00-Infinity") || !AR.equals("0.00-infinity")) && continueProcessing) {
					double ARMin = Double.parseDouble(AR.substring(0, AR.indexOf("-")));
					double ARMax=999999999;
					String ARInterMax = AR.substring(AR.indexOf("-")+1);
					if(ARInterMax.equals("Infinity") || ARInterMax.equals("infinity")) {
						ARMax=999999999;
					} else {
						ARMax = Double.parseDouble(AR.substring(AR.indexOf("-")+1));
					}
					if(initialResultsTable.getValue("AR", n)<ARMin || initialResultsTable.getValue("AR", n)>ARMax) {
						filledImage.fill8(X[n], Y[n]);
						continueProcessing=true;
						//IJ.log("AR");
					}
			}
	
			if((!FeretAR.equals("0.00-Infinity") || !FeretAR.equals("0.00-infinity")) && continueProcessing) {
					double FARMin = Double.parseDouble(FeretAR.substring(0, FeretAR.indexOf("-")));
					double FARMax = 999999999.9;
					String FARInterMax = FeretAR.substring(FeretAR.indexOf("-")+1);
					if(FARInterMax=="Infinity" || FARInterMax=="infinity") {
						FARMax=999999999.9;
					} else {
						FARMax = Double.parseDouble(FeretAR.substring(FeretAR.indexOf("-")+1));
					}
					if(FAR[n]<FARMin || FAR[n]>FARMax) {
						filledImage.fill8(X[n], Y[n]);
						continueProcessing=true;
						//IJ.log("FeretAR");
					}
			}
			
			if(!EllipsoidAngle.equals("0-180") && continueProcessing) {
					double EllipsoidAngleMin = Double.parseDouble(EllipsoidAngle.substring(0, EllipsoidAngle.indexOf("-")));
					double EllipsoidAngleMax = Double.parseDouble(EllipsoidAngle.substring(EllipsoidAngle.indexOf("-")+1));
					if(initialResultsTable.getValue("Angle", n)<EllipsoidAngleMin || initialResultsTable.getValue("Angle", n)>EllipsoidAngleMax) {
						filledImage.fill8(X[n], Y[n]);
						continueProcessing=true;
						//IJ.log("EllipsoidAngle");
					}
			}
			
			if((!MaxFeret.equals("0.00-Infinity") || !MaxFeret.equals("0.00-infinity")) && continueProcessing) {
					double MaxFeretMin = Double.parseDouble(MaxFeret.substring(0, MaxFeret.indexOf("-")));
					double MaxFeretMax=999999999.9;
					String MaxFeretInterMax = MaxFeret.substring(MaxFeret.indexOf("-")+1);
					if(MaxFeretInterMax.equals("Infinity") || MaxFeretInterMax.equals("infinity")) {
						MaxFeretMax=999999999.9;
					} else {
						MaxFeretMax = Double.parseDouble(MaxFeret.substring(MaxFeret.indexOf("-")+1));
					}
					double currentMaxFeret = initialResultsTable.getValue("Feret", n);
					if(!unit.equals("pixels") || !unit.equals("pixel")) {
						if(usePixel) {
							currentMaxFeret = currentMaxFeret / pixelWidth;
						}
					}
					if(currentMaxFeret<MaxFeretMin || currentMaxFeret>MaxFeretMax) {
						filledImage.fill8(X[n], Y[n]);
						continueProcessing=true;
						//IJ.log("MaxFeret");
					}
			}
				
			if(!FeretAngle.equals("0-180") && continueProcessing) {
					double FeretAngleMin = Double.parseDouble(FeretAngle.substring(0, FeretAngle.indexOf("-")));
					double FeretAngleMax = Double.parseDouble(FeretAngle.substring(FeretAngle.indexOf("-")+1));
					if(initialResultsTable.getValue("FeretAngle", n)<FeretAngleMin || initialResultsTable.getValue("FeretAngle", n)>FeretAngleMax) {
						filledImage.fill8(X[n], Y[n]);
						continueProcessing=true;
						//IJ.log("FeretAngle");
					}
			}
	
			if(!COV.equals("0.00-1.00") && continueProcessing) {
					double COVMin = Double.parseDouble(COV.substring(0, COV.indexOf("-")));
					double COVMax = Double.parseDouble(COV.substring(COV.indexOf("-")+1));
					if(cov[n]<COVMin || cov[n]>COVMax) {
						filledImage.fill8(X[n], Y[n]);
						continueProcessing=true;
						//IJ.log("COV");
					}
			}

			
			if(continueProcessing=true) {
				keptResults[KeptResultsCount] = n;
				KeptResultsCount++;
			}			
		}
		initialResultsTable = null;

		ResultsTable outputResultsTable;
		if(existingResultsTableWindow!=null) {
			IJ.renameResults("oldResultsTable", "Results");
			outputResultsTable = ResultsTable.getResultsTable();
		} else {
			outputResultsTable = new ResultsTable();
		}
		
		if(rec!=null) {
			 Recorder.record = true;
		}
		
		int existingResultsCounter = 0;
		ResultsTable resultsTable = new ResultsTable();
		
		int currentResultCount = 0;
		String newLabel = "";
		
		if(existingResultsTableWindow!=null && !ClearResults) {
			ParticleAnalyzer outputPA = new ParticleAnalyzer(outputOptions, measurementFlags, resultsTable, AreaMin, AreaMax);
			outputPA.analyze(tempImg);
			outputImg = outputPA.getOutputImage();
			currentResultCount = resultsTable.getCounter();
			int currentColumnCount = resultsTable.getLastColumn();

			existingResultsCounter = outputResultsTable.getCounter();

			for(int row=0; row<currentResultCount; row++) {
				for(int column=0;column<=currentColumnCount; column++) {
					if(column==0) {
						outputResultsTable.incrementCounter();
						outputResultsTable.addValue(column, resultsTable.getValueAsDouble(column, row));
						outputResultsTable.addLabel(originalImageTitle);
						
					} else if(column!=0 && (column<26 || column>28)) {
						try {
							outputResultsTable.getStringValue(column, row);
						}
						catch(Exception e) {
							outputResultsTable.setValue(column, row, 0);
						}
						
						outputResultsTable.setValue(column, (row+existingResultsCounter), resultsTable.getValueAsDouble(column, row));
					}
					
				}
			}
		} else if(existingResultsTableWindow==null || ClearResults) {
			ParticleAnalyzer outputPA = new ParticleAnalyzer(outputOptions, measurementFlags, outputResultsTable, AreaMin, AreaMax);
			outputPA.analyze(tempImg);
			outputImg = outputPA.getOutputImage();
			for(int l=0; l<outputResultsTable.getCounter(); l++) {
				outputResultsTable.setLabel(originalImageTitle, l);
			}
		}

		if(Output.equals("Nothing")) {
			imp2.close();
		} else if(Output.equals("Overlay Outlines")) {
			IJ.selectWindow(outputImgID);
			IJ.run("Invert");
			IJ.run("Red");
			IJ.selectWindow(originalImgID);
			IJ.run("Add Image...", "image=["+outputImgTitle+"] x=0 y=0 opacity=75 zero");
			outputImg.changes = false;
			outputImg.close();
		} else if(Output.equals("Overlay Masks")) {
			IJ.selectWindow(outputImgID);
			IJ.run("Cyan");
			IJ.selectWindow(originalImgID);
			IJ.run("Add Image...", "image=["+outputImgTitle+"] x=0 y=0 opacity=75 zero");
			outputImg.changes = false;
			outputImg.close();
		} else if(!Output.equals("Overlay Outlines") && !Output.equals("Overlay Masks") && !Output.equals("Nothing")) {
			ImageProcessor outputIP = outputImg.getProcessor();
			outputImgTitle = outputImg.getTitle();
			outputImgID = outputImg.getID();
			outputImg.updateAndDraw();
		}

		//potentially include convexity calculation here
		if(DisplayResults) {
			int finalResultNumber = outputResultsTable.getCounter();
			int keptResultsCounter = 0;
			for(int writeNew=existingResultsCounter; writeNew<finalResultNumber; writeNew++) {
				outputResultsTable.setValue("FeretAR", writeNew, FAR[keptResults[keptResultsCounter]]);
				outputResultsTable.setValue("Compact", writeNew, compactness[keptResults[keptResultsCounter]]);
				outputResultsTable.setValue("Extent", writeNew, extent[keptResults[keptResultsCounter]]);
				if(!Redirect.equals("None")) {
					outputResultsTable.setValue("COV", writeNew, cov[keptResults[keptResultsCounter]]);
					outputResultsTable.setValue("Mean", writeNew, originalMeanValue[keptResults[keptResultsCounter]]);
					outputResultsTable.setValue("Median", writeNew, originalMedian[keptResults[keptResultsCounter]]);
					outputResultsTable.setValue("Mode", writeNew, originalMode[keptResults[keptResultsCounter]]);
					outputResultsTable.setValue("StdDev", writeNew, originalStdDev[keptResults[keptResultsCounter]]);
					outputResultsTable.setValue("IntDen", writeNew, originalIntDen[keptResults[keptResultsCounter]]);
					outputResultsTable.setValue("RawIntDen", writeNew, originalRawIntDen[keptResults[keptResultsCounter]]);
					outputResultsTable.setValue("Min", writeNew, originalMin[keptResults[keptResultsCounter]]);
					outputResultsTable.setValue("Max", writeNew, originalMax[keptResults[keptResultsCounter]]);
					outputResultsTable.setValue("Skew", writeNew, originalSkewness[keptResults[keptResultsCounter]]);
					outputResultsTable.setValue("Kurt", writeNew, originalKurtosis[keptResults[keptResultsCounter]]);	
				}
				keptResultsCounter++;
			}
			
			outputResultsTable.show("Results");
		}

		//Default value definition
		if(Reset) {
			resetDialogEntries();
		}
	}

	//------------------------------------------------------------------------------------------------------------------------

	public void closeLogWindow() {
		if(!IJ.getLog().equals(null)) {
			IJ.selectWindow("Log"); 
			IJ.run("Close"); 
		}
	}

	//------------------------------------------------------------------------------------------------------------------------

	public String[] getImageNames(ImagePlus imp1) {
		int[] imageIDList = WindowManager.getIDList();
		String[] imageNames = new String[imageIDList.length+1];
		imageNames[0] = "None";
		if(!imageIDList.equals(null)) {
			for(int i=0; i<imageIDList.length; i++){
			        ImagePlus tempIMP = WindowManager.getImage(imageIDList[i]);
			        imageNames[i+1] = tempIMP.getTitle();
			}
			return imageNames;
		} else {
			IJ.error("no open images detected");
			return imageNames;
		}
	}

	//------------------------------------------------------------------------------------------------------------------------

	public ImageProcessor borderCountCorrection(ImageProcessor inputIP, String correctionPosition) {
		
		if(!correctionPosition.equals("None")) {
			Prefs.set("resizer.zero", false);
			ij.Prefs.blackBackground = true;
			IJ.setBackgroundColor(255,255,255);
			CanvasResizer resizeCanvas = new CanvasResizer(); 
			int xOff = 0;
			int yOff = 0;
	
			if(correctionPosition.equals("Top-Left")) {
				xOff = 0;
				yOff = 0;
			} else if(correctionPosition.equals("Top-Right")) {
				xOff = 1;
				yOff = 0;
			} else if(correctionPosition.equals("Bottom-Left")) {
				xOff = 0;
				yOff = 1;				
			} else if(correctionPosition.equals("Bottom-Right")) {
				xOff = 1;
				yOff = 1;				
			}

			
			ImageProcessor intermediateIP = resizeCanvas.expandImage(inputIP, (width+1), (height+1), xOff, yOff);
						
			FloodFiller bcFF = new FloodFiller(intermediateIP);
			intermediateIP.setValue(0);
			
			//ImagePlus intermediateIMP = new ImagePlus("test imp", intermediateIP);	//test output
			//intermediateIMP.show();	//test output
			if(correctionPosition.equals("Top-Left") || correctionPosition.equals("Top-Right") || correctionPosition.equals("Bottom-Left")) {
				bcFF.fill8(width, height);
			} else if(correctionPosition.equals("Bottom-Right")) {
				bcFF.fill8(0, 0);
			}
			ImageProcessor ip2 = resizeCanvas.expandImage(intermediateIP, (width), (height), (-xOff), (-yOff));
			return ip2;
		} else {
			ImageProcessor ip2 = inputIP.duplicate();
			return ip2;
		}
	}

	//------------------------------------------------------------------------------------------------------------------------

	private void defineParticleAnalyzers() {
		//set the output options as in the particle analyzer
		//IJ.run("Set Measurements...", "area mean standard modal min centroid center perimeter bounding fit shape feret's integrated median skewness kurtosis area_fraction stack display redirect=None decimal=3");
		if(Output.equals("Nothing")) {
			outputOptions |= ParticleAnalyzer.SHOW_NONE;
		} else if(Output.equals("Outlines")) {
			outputOptions |= ParticleAnalyzer.SHOW_OUTLINES;
		} else if(Output.equals("Masks")) {
			outputOptions |= ParticleAnalyzer.SHOW_MASKS;
		} else if(Output.equals("Count Masks")) {
			outputOptions |= ParticleAnalyzer.SHOW_ROI_MASKS;
		} else if(Output.equals("Overlay Outlines")) {
			outputOptions |= ParticleAnalyzer.SHOW_OUTLINES;
		} else if(Output.equals("Overlay Masks")) {
			outputOptions |= ParticleAnalyzer.SHOW_MASKS;
		}

		if(DisplayResults) {
			outputOptions |= ParticleAnalyzer.SHOW_RESULTS;
		} else {
			outputOptions &= ~ParticleAnalyzer.SHOW_RESULTS;
		}

		if(ClearResults) {
			outputOptions |= ParticleAnalyzer.CLEAR_WORKSHEET;
		} else {
			outputOptions &= ~ParticleAnalyzer.CLEAR_WORKSHEET;
		}

		if(Summarize) {
			outputOptions |= ParticleAnalyzer.DISPLAY_SUMMARY;
		} else {
			outputOptions &= ~ParticleAnalyzer.DISPLAY_SUMMARY;
		}

		if(AddToManager) {
			outputOptions |= ParticleAnalyzer.ADD_TO_MANAGER;
		} else {
			outputOptions &= ~ParticleAnalyzer.ADD_TO_MANAGER;
		}
		
		if(ExcludeEdges) {
			currentPAOptions |= ParticleAnalyzer.EXCLUDE_EDGE_PARTICLES;
			outputOptions |= ParticleAnalyzer.EXCLUDE_EDGE_PARTICLES;
		} else {
			currentPAOptions &= ~ParticleAnalyzer.EXCLUDE_EDGE_PARTICLES;
			outputOptions &= ~ParticleAnalyzer.EXCLUDE_EDGE_PARTICLES;
		}

		if(IncludeHoles) {
			currentPAOptions |= ParticleAnalyzer.INCLUDE_HOLES;
			outputOptions |= ParticleAnalyzer.INCLUDE_HOLES;
		} else {
			currentPAOptions &= ~ParticleAnalyzer.INCLUDE_HOLES;
			outputOptions &= ~ParticleAnalyzer.INCLUDE_HOLES;
		}
	}

	//------------------------------------------------------------------------------------------------------------------------

	private void resetDialogEntries() {
		Area="0-Infinity";
		Prefs.set("advPartAnal.area", Area);
		Extent="0.00-1.00";
		Prefs.set("advPartAnal.Extent", Extent);
		Perimeter="0-Infinity";
		Prefs.set("advPartAnal.perimeter", Perimeter);
		Circularity="0.00-1.00";
		Prefs.set("advPartAnal.circularity", Circularity);
		Roundness="0.00-1.00";
		Prefs.set("advPartAnal.roundness", Roundness);
		Solidity="0.00-1.00";
		Prefs.set("advPartAnal.solidity", Solidity);
		Compactness="0.00-1.00";
		Prefs.set("advPartAnal.compactness", Compactness);
		AR="0-Infinity";
		Prefs.set("advPartAnal.AR", AR);
		FeretAR="0-Infinity";
		Prefs.set("advPartAnal.FAR", FeretAR);
		EllipsoidAngle="0-180";
		Prefs.set("advPartAnal.angle", EllipsoidAngle);
		MaxFeret="0-Infinity";
		Prefs.set("advPartAnal.max.feret", MaxFeret);
		FeretAngle="0-180";
		Prefs.set("advPartAnal.feret.angle", FeretAngle);
		COV="0.00-1.00";
		Prefs.set("advPartAnal.variation.coefficient", COV);	
		Output="Masks";
		Prefs.set("advPartAnal.show", Output);
		Correction="None";
		Prefs.set("advPartAnal.borderCountCorrection", Correction);
		//checkbox default reset
		DisplayResults=true;
		Prefs.set("advPartAnal.CB0", DisplayResults);
		ClearResults = false;
		Prefs.set("advPartAnal.CB1", ClearResults);
		Summarize=false;
		Prefs.set("advPartAnal.CB2", Summarize);
		AddToManager=false;
		Prefs.set("advPartAnal.CB3", AddToManager);
		ExcludeEdges=false;
		Prefs.set("advPartAnal.CB4", ExcludeEdges);
		IncludeHoles=false;
		Prefs.set("advPartAnal.CB5", IncludeHoles);
		Reset=false;
		Prefs.set("advPartAnal.CB6", Reset);
	}

	//------------------------------------------------------------------------------------------------------------------------
	
	public void testValidUserInput(String inputParameter) {
		String lowerInputParameter;
		String higherInputParameter;
		try {
			lowerInputParameter = inputParameter.substring(0, inputParameter.indexOf("-"));
			higherInputParameter = inputParameter.substring(inputParameter.indexOf("-")+1);
		}
		catch(StringIndexOutOfBoundsException sioobe) {
			IJ.error("missing '-' between parameter");
			return;
		}
		
		try {  
			double lowerValue = Double.parseDouble(lowerInputParameter);	
		} 
		catch(NumberFormatException nfe) {
			if(lowerInputParameter!="Infinity" || lowerInputParameter!="infinity") {
				resetDialogEntries();
				IJ.error("Invalid parameter entry");
				return;
			} else {
				//continue
			}
			 
		}
		
		try {  
			double higherValue = Double.parseDouble(higherInputParameter);	
		} 
		catch(NumberFormatException nfe) {
			if(higherInputParameter!="Infinity" || higherInputParameter!="infinity") {
				resetDialogEntries();
				IJ.error("Invalid parameter entry");
				return;
			} else {
				//continue
			}
		}
		
		if(Double.parseDouble(lowerInputParameter) > Double.parseDouble(higherInputParameter)) {
			resetDialogEntries();
			IJ.error("min value bigger than max value");
			return;
		}
		
	}
	
	public void testValidAngle(String userInputAngle) {
		double lowerInputAngle = Double.parseDouble(userInputAngle.substring(0, userInputAngle.indexOf("-")));
		double higherInputAngle = Double.parseDouble(userInputAngle.substring(userInputAngle.indexOf("-")+1));
		if(lowerInputAngle<0.0 || higherInputAngle>180.0) {
			resetDialogEntries();
			IJ.error("Invalid angle entered (range: 0-180)");
			return;
		}
	}
	
	public void testValidShapeDescriptor(String userInputShapeDescriptor) {
		double lowerInputShapeDescriptor = Double.parseDouble(userInputShapeDescriptor.substring(0, userInputShapeDescriptor.indexOf("-")));
		double higherInputShapeDescriptor = Double.parseDouble(userInputShapeDescriptor.substring(userInputShapeDescriptor.indexOf("-")+1));
		if(lowerInputShapeDescriptor<0.0 || higherInputShapeDescriptor>1.0) {
					resetDialogEntries();
					IJ.error("Invalid parameter entry");
					return;
				}
	}
	
	//------------------------------------------------------------------------------------------------------------------------

	public void setNPasses(int nPasses) {
		this.nPasses = nPasses;
		pass = 0;
	}
}