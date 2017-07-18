package org.cogcomp.md;

/*
 * The reader file which reads B/I/O tag for each word of a certain corpus
 * Supports mode:
 * "ACE05" -> ACE 2005 with ACEReader
 * It returns Constituents duplicated from Token View, all the returning Constituents should be of size 1
 * The returning Constituents has an attribute "BIO", with value "B","I" or "O"
 * Example usage: Parser parser = new BIOReader("data/", "ACE05");
 */
import java.io.File;
import java.util.*;

import edu.illinois.cs.cogcomp.annotation.Annotator;
import edu.illinois.cs.cogcomp.core.resources.ResourceConfigurator;
import edu.illinois.cs.cogcomp.lbjava.parse.Parser;
import edu.illinois.cs.cogcomp.ner.ExpressiveFeatures.BrownClusters;
import edu.illinois.cs.cogcomp.ner.ExpressiveFeatures.FlatGazetteers;
import edu.illinois.cs.cogcomp.ner.ExpressiveFeatures.Gazetteers;
import edu.illinois.cs.cogcomp.ner.ExpressiveFeatures.GazetteersFactory;
import edu.illinois.cs.cogcomp.nlp.corpusreaders.ACEReader;
import edu.illinois.cs.cogcomp.core.datastructures.textannotation.*;
import edu.illinois.cs.cogcomp.core.datastructures.ViewNames;
import edu.illinois.cs.cogcomp.nlp.corpusreaders.ereReader.EREDocumentReader;
import edu.illinois.cs.cogcomp.nlp.corpusreaders.ereReader.EREMentionRelationReader;
import edu.illinois.cs.cogcomp.pos.POSAnnotator;
import org.cogcomp.Datastore;

public class BIOReader implements Parser
{
    private List<Constituent> tokenList;
    private int tokenIndex;
    private List<TextAnnotation> taList;
    private String _path;
    private String _mode;
    private String _type;
    private List<Annotator> annotators;

    public String id;


    public BIOReader(String path, String mode, String type){
        _path = path;
        _mode = mode;
        _type = type;
        String[] path_group = path.split("/");
        String group = path_group[path_group.length - 1];
        id = group + "_" + type;
        taList = getTextAnnotations();
        annotateTas();
        tokenList = getTokensFromTAs();
    }

    private List<TextAnnotation> getTextAnnotations(){
        List<TextAnnotation> ret = new ArrayList<>();
        if (_mode.equals("ACE05")){
            ACEReader aceReader = null;
            try{
                aceReader = new ACEReader(_path, false);
            }
            catch (Exception e){
                e.printStackTrace();
            }
            for (TextAnnotation ta : aceReader) {
                ret.add(ta);
            }
        }
        else if (_mode.equals("ERE")){
            EREMentionRelationReader ereMentionRelationReader = null;
            try {
                ereMentionRelationReader = new EREMentionRelationReader(EREDocumentReader.EreCorpus.ENR3, _path, false);

            }
            catch (Exception e){
                e.printStackTrace();
            }
            for (XmlTextAnnotation xta : ereMentionRelationReader){
                ret.add(xta.getTextAnnotation());
            }
        }
        else{
            System.out.println("No defult actions for unknown mode");
        }
        return ret;
    }

    private void annotateTas(){
        for (TextAnnotation ta : taList){
            POSAnnotator posAnnotator = new POSAnnotator();
            try {
                ta.addView(posAnnotator);
            }
            catch (Exception e){
                e.printStackTrace();
            }
        }
    }

    private List<Constituent> getTokensFromTAs(){
        List<Constituent> ret = new ArrayList<>();
        try {
            Datastore ds = new Datastore(new ResourceConfigurator().getDefaultConfig());
            File gazetteersResource = ds.getDirectory("org.cogcomp.gazetteers", "gazetteers", 1.3, false);
            GazetteersFactory.init(5, gazetteersResource.getPath() + File.separator + "gazetteers", true);
            Vector<String> bcs = new Vector<>();
            bcs.add("brown-clusters/brown-english-wikitext.case-intact.txt-c1000-freq10-v3.txt");
            bcs.add("brown-clusters/brownBllipClusters");
            bcs.add("brown-clusters/brown-rcv1.clean.tokenized-CoNLL03.txt-c1000-freq1.txt");
            Vector<Integer> bcst = new Vector<>();
            bcst.add(5);
            bcst.add(5);
            bcst.add(5);
            Vector<Boolean> bcsl = new Vector<>();
            bcsl.add(false);
            bcsl.add(false);
            bcsl.add(false);
            BrownClusters.init(bcs, bcst, bcsl);
        }
        catch (Exception e){
            e.printStackTrace();
        }
        Gazetteers gazetteers = GazetteersFactory.get();
        BrownClusters brownClusters = BrownClusters.get();
        String mentionViewName = "";
        if (_mode.equals("ACE05")){
            mentionViewName = ViewNames.MENTION_ACE;
        }
        else if (_mode.equals("ERE")){
            mentionViewName = ViewNames.MENTION_ERE;
        }
        else{
            System.out.println("No actions for undefined mode");
        }
        for (TextAnnotation ta : taList){
            View tokenView = ta.getView(ViewNames.TOKENS);
            View mentionView = ta.getView(mentionViewName);
            View bioView = new SpanLabelView("BIO", BIOReader.class.getCanonicalName(), ta, 1.0f);
            String[] token2tags = new String[tokenView.getConstituents().size()];
            for (int i = 0; i < token2tags.length; i++){
                token2tags[i] = "O";
            }
            for (Constituent c : mentionView.getConstituents()){
                if (!_type.equals("ALL")) {
                    String excludeType = _type;
                    if (_type.startsWith("SPE_")){
                        excludeType = _type.substring(4);
                    }
                    if (!c.getAttribute("EntityMentionType").equals(excludeType)) {
                        continue;
                    }
                    Constituent cHead = ACEReader.getEntityHeadForConstituent(c, ta, "HEAD");
                    if (cHead.getStartSpan()+1 == cHead.getEndSpan()) {
                        token2tags[cHead.getStartSpan()] = "U," + c.getAttribute("EntityMentionType");
                    }
                    else {
                        token2tags[cHead.getStartSpan()] = "B," + c.getAttribute("EntityMentionType");
                        for (int i = cHead.getStartSpan() + 1; i < cHead.getEndSpan() - 1; i++) {
                            token2tags[i] = "I," + c.getAttribute("EntityMentionType");
                        }
                        token2tags[cHead.getEndSpan() - 1] = "L," + c.getAttribute("EntityMentionType");
                    }
                }
                Constituent cHead = ACEReader.getEntityHeadForConstituent(c, ta, "HEAD");
                if (cHead == null){
                    continue;
                }
                token2tags[cHead.getStartSpan()] = "B," + c.getAttribute("EntityMentionType");
                for (int i = cHead.getStartSpan() + 1; i < cHead.getEndSpan(); i++){
                    token2tags[i] = "I," + c.getAttribute("EntityMentionType");
                }
            }
            if (_type.equals("SPE_PRO")){
                for (Constituent c : ta.getView(ViewNames.POS)){
                    String posLabel = c.getLabel();
                    if (posLabel.contains("PRP") || posLabel.contains("WP")){
                        if (c.getEndSpan() - 1 == c.getStartSpan()){
                            token2tags[c.getStartSpan()] = "B," + c.getAttribute("EntityMentionType");
                        }
                    }
                }
            }
            for (int i = 0; i < token2tags.length; i++){
                Constituent curToken = tokenView.getConstituentsCoveringToken(i).get(0);
                Constituent newToken = curToken.cloneForNewView("BIO");
                if (token2tags[i].equals("O")) {
                    newToken.addAttribute("BIO", token2tags[i]);
                }
                else{
                    String[] group = token2tags[i].split(",");
                    String tag = group[0];
                    String eml = group[1];
                    newToken.addAttribute("BIO", tag);
                    newToken.addAttribute("EntityMentionType", eml);
                }
                newToken.addAttribute("GAZ", ((FlatGazetteers)gazetteers).annotateConstituent(newToken));
                newToken.addAttribute("BC", brownClusters.getPrefixesCombined(newToken.toString()));
                if (_path.contains("train")){
                    newToken.addAttribute("isTraining", "true");
                }
                else{
                    newToken.addAttribute("isTraining", "false");
                }
                bioView.addConstituent(newToken);
            }
            ta.addView("BIO", bioView);
            for (Constituent c : bioView){
                ret.add(c);
            }
        }
        return ret;
    }

    public void close(){}
    public Object next(){
        if (tokenIndex == tokenList.size()) {
            return null;
        } else {
            tokenIndex++;
            return tokenList.get(tokenIndex - 1);
        }
    }

    public void reset(){
        tokenIndex = 0;
    }
}
