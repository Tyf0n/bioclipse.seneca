/* HHCOSYJudge.java
 *
 * Copyright (C) 1997, 1998, 1999, 2000  Christoph Steinbeck
 *
 * Contact: c.steinbeck@uni-koeln.de
 *
 * This software is published and distributed under artistic license.
 * The intent of this license is to state the conditions under which this Package
 * may be copied, such that the Copyright Holder maintains some semblance
 * of artistic control over the development of the package, while giving the
 * users of the package the right to use and distribute the Package in a
 * more-or-less customary fashion, plus the right to make reasonable modifications.
 *
 * THIS PACKAGE IS PROVIDED "AS IS" AND WITHOUT ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, WITHOUT LIMITATION, THE IMPLIED WARRANTIES OF MERCHANTIBILITY AND
 * FITNESS FOR A PARTICULAR PURPOSE.
 *
 * The complete text of the license can be found in a file called LICENSE
 * accompanying this package.
 */

package net.bioclipse.seneca.judge;

import java.util.ArrayList;

import net.bioclipse.chemoinformatics.wizards.WizardHelper;
import net.bioclipse.core.util.LogUtils;
import net.bioclipse.seneca.judge.AbstractTwoDSpectrumJudge.TwoDRule;
import nu.xom.Document;
import nu.xom.Elements;

import org.apache.log4j.Logger;
import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.Shell;
import org.openscience.cdk.interfaces.IAtomContainer;
import org.xmlcml.cml.base.CMLBuilder;
import org.xmlcml.cml.base.CMLUtil;
import org.xmlcml.cml.element.CMLCml;
import org.xmlcml.cml.element.CMLSpectrum;

import spok.utils.SpectrumUtils;

/**
 * Gets the AllPairsShortestPath matrix for a given structure and checks if all
 * of the HHCOSY rules are fullfilled. HHCOSY rules are given as a 3D matrix of
 * size [n][n][n] where n is the number of atoms in the structure. A value != 0
 * at [x][y][y] indicates that there was a HHCOSY crosspeak between the signals
 * of heavyatom x and y. In the third dimension alternatives to y are noted to
 * handle ambigous assignments.
 */

public class HHCOSYJudge extends AbstractTwoDSpectrumJudge {

	private CMLCml cmlcml;
	private static Logger logger = Logger.getLogger(HHCOSYJudge.class);
	
	public HHCOSYJudge() {
		super("HHCOSYJudge");
		setScore(100, 0);
	}

	public IJudge createJudge(IPath data) throws MissingInformationException {
		this.setData( data );
        CMLBuilder builder = new CMLBuilder();
        try{
            Document doc =  builder.buildEnsureCML(ResourcesPlugin.getWorkspace().getRoot().getFile( data).getContents());
            SpectrumUtils.namespaceThemAll( doc.getRootElement().getChildElements() );
            doc.getRootElement().setNamespaceURI(CMLUtil.CML_NS);
            cmlcml = (CMLCml)builder.parseString(doc.toXML());
            couplings = new ArrayList<TwoDRule>();
    		//using the hmbc spectrum, we build couplings.
    		Elements spectra = cmlcml.getChildCMLElements("spectrum");
    		for(int i=0;i<spectra.size();i++){
    			CMLSpectrum spectrum = (CMLSpectrum)spectra.get(i);
    			if(spectrum.getType().equals("HHCOSY")){
    				for(int k=0;k<spectrum.getPeakListElements().get(0).getPeakElements().size();k++){
    					couplings.add(new TwoDRule(spectrum.getPeakListElements().get(0).getPeakElements().get(k).getXValue(), spectrum.getPeakListElements().get(0).getPeakElements().get(k).getYValue()));
    				}
    			}
    		}
            return this;
        }catch(Exception ex){
            throw new MissingInformationException("Could not read the cmlString.");
        }
	}

    public String getDescription() {
    	return "A simple HHCOSY scoring function. Configuration is in a jsx file right now";
    }

    public boolean checkJudge( String data ) {
        try{
        	check(data);
        	return true;
        }catch(Exception ex){
        	return false;
        }
    }
    
    private void check(String data) throws MissingInformationException {
        CMLBuilder builder = new CMLBuilder();
        int correctspectra=0;
        int peakspectra=0;
        Document doc;
		try {
			doc = builder.buildEnsureCML(((IFile)ResourcesPlugin.getWorkspace().getRoot().findMember(data)).getContents());
	        SpectrumUtils.namespaceThemAll( doc.getRootElement().getChildElements() );
	        doc.getRootElement().setNamespaceURI(CMLUtil.CML_NS);
	        CMLCml cmlcml = (CMLCml)builder.parseString(doc.toXML());
	        Elements spectra = cmlcml.getChildCMLElements("spectrum");
	        for(int i=0;i<spectra.size();i++){
	        	CMLSpectrum spectrum = (CMLSpectrum)spectra.get(i);
	        	if(spectrum.getType().equals("NMR")){
	        		correctspectra++;
	        	}else if(spectrum.getType().equals("HHCOSY")){
	        		correctspectra++;
	        	}else if(spectrum.getType().equals("HSQC")){
	        		correctspectra++;
	        	}
	        	if(spectrum.getPeakListElements().size()==1)
	        		peakspectra++;
	        }
	        if(correctspectra!=3)
	        	throw new MissingInformationException("We need spectra of type NMR, HSQC and HHCOSY!");
	        else if(peakspectra!=3)
	        	throw new MissingInformationException("All spectra must have one peaklist!");
		} catch (Exception e) {
			throw new MissingInformationException("Cannot read file: "+e.getMessage());
		}
    }

    public IFile setData( ISelection selection, IFile sjsFile ) {
        IStructuredSelection ssel = (IStructuredSelection) selection;
        if(ssel.size()>1){
            MessageBox mb = new MessageBox(new Shell(), SWT.ICON_WARNING);
            mb.setText("Multiple Files");
            mb.setMessage("Only one file can be dropped on here!");
            mb.open();
            return null;
        }else{
            if (ssel.getFirstElement() instanceof IFile) {
                IFile file = (IFile) ssel.getFirstElement();
                try{
                	check(file.getFullPath().toOSString());
                }catch (Exception e) {
                    MessageBox mb = new MessageBox(new Shell(), SWT.ICON_WARNING);
                    mb.setText("File not correct");
                    mb.setMessage(e.getMessage()+" Please edit the file manually!");
                    mb.open();
                    return null;
				}
                //if the file is somewhere else, we make a new file
                IFile newFile;
                if(!file.getParent().getFullPath().toOSString().equals(sjsFile.getParent().getFullPath().toOSString())){
                    IContainer folder = sjsFile.getParent();
                    String newFileName;
                    if(file.getParent()==sjsFile.getParent())
                        newFileName=file.getName().substring( 0, file.getName().length()-1-file.getFileExtension().length() )+"peaks";
                    else
                        newFileName=file.getName().substring( 0, file.getName().length()-1-file.getFileExtension().length() );
                    IStructuredSelection projectFolder = 
                        new StructuredSelection(
                                folder);
                    String filename = WizardHelper.
                    findUnusedFileName(
                        projectFolder, newFileName, ".cml");
                    newFile = folder.getFile( new Path(filename));
                    try {
						newFile.create(file.getContents(),0, new NullProgressMonitor());
					} catch (CoreException e) {
						LogUtils.handleException(e, logger,net.bioclipse.seneca.Activator.PLUGIN_ID);
					}
                }else{
                    newFile = file;
                }
                return newFile;
            }else{
                MessageBox mb = new MessageBox(new Shell(), SWT.ICON_WARNING);
                mb.setText("Not a file");
                mb.setMessage("Only a file (not directory etc.) can be dropped on here!");
                mb.open();
                return null;
            }
        }
    }

	public boolean isLabelling() {
		return true;
	}

	public void labelStartStructure(IAtomContainer startStructure) {
		//using the bb+hsqc spectrum, we assign c and h labels.
		Elements spectra = cmlcml.getChildCMLElements("spectrum");
		for(int i=0;i<spectra.size();i++){
			CMLSpectrum spectrum = (CMLSpectrum)spectra.get(i);
			if(spectrum.getType().equals("NMR")){
				for(int k=0;k<spectrum.getPeakListElements().get(0).getPeakElements().size();k++){
					for(int l=0;l<startStructure.getAtomCount();l++){
						if(startStructure.getAtom(l).getProperty(HMBCJudge.C_SHIFT)==null && startStructure.getAtom(l).getImplicitHydrogenCount()==Integer.parseInt(spectrum.getPeakListElements().get(0).getPeakElements().get(k).getAttributeValue("multiplicity"))){
							startStructure.getAtom(l).setProperty(HMBCJudge.C_SHIFT,spectrum.getPeakListElements().get(0).getPeakElements().get(k).getXValue());
							break;
						}
					}
				}
			}
		}
		for(int i=0;i<spectra.size();i++){
			CMLSpectrum spectrum = (CMLSpectrum)spectra.get(i);
			if(spectrum.getType().equals("HSQC")){
				for(int k=0;k<spectrum.getPeakListElements().get(0).getPeakElements().size();k++){
					for(int l=0;l<startStructure.getAtomCount();l++){
						if((Double)startStructure.getAtom(l).getProperty(HMBCJudge.C_SHIFT)!=null && (Double)startStructure.getAtom(l).getProperty(HMBCJudge.C_SHIFT)==spectrum.getPeakListElements().get(0).getPeakElements().get(k).getXValue()){
							if(startStructure.getAtom(l).getProperty(HMBCJudge.H_SHIFT)==null)
								startStructure.getAtom(l).setProperty(HMBCJudge.H_SHIFT,spectrum.getPeakListElements().get(0).getPeakElements().get(k).getYValue());
							else
								startStructure.getAtom(l).setProperty(HMBCJudge.H_SHIFT_2,spectrum.getPeakListElements().get(0).getPeakElements().get(k).getYValue());
							break;
						}
					}
				}
			}
		}
		/*for(int l=0;l<startStructure.getAtomCount();l++){
			System.err.println(startStructure.getAtom(l).getProperty(C_SHIFT)+" "+startStructure.getAtom(l).getProperty(H_SHIFT)+" "+startStructure.getAtom(l).getProperty(H_SHIFT_2));
		}*/
	}
}
