package com.azdegar.translator;

import com.azdegar.nlp.AzdegarAnnotations;
import com.azdegar.nlp.SentenceType;
import com.azdegar.nlp.Clause;
import com.azdegar.nlp.ExtWord;
import com.azdegar.nlp.WordGroup;
import com.azdegar.nlp.EnglishAnalyzer;
import com.azdegar.nlp.TypeRepository;
import edu.stanford.nlp.ling.CoreLabel;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 *
 * @author Afshin Pouria
 */
public class SovService {

    private final List<String> logs;

    public SovService(List<String> logs) {
        this.logs = logs;
    }

    public SovClause build(Clause clause, Clause main, SovClause parent, List<CoreLabel> whole, TypeRepository typeRepository) {
        SovClause sov;
        boolean dependent = clause.getParent() != null;
        if (clause.words().size() < 2) {
            sov = new SovClause(clause.words(), dependent);
        } else {
            EnglishAnalyzer analyzer = new EnglishAnalyzer(logs);
            analyzer.setTypeRepository(typeRepository);

            analyzer.detectTenseVoice(clause, parent);
            analyzer.detectType(clause, parent, dependent && clause.getOpening() == null);
            Map<String, WordGroup> parts = analyzer.identifyParts(clause);
            sov = new SovClause(clause, parts);
            if (parts.get("subj") != null && !parts.get("subj").isEmpty() && parts.get("subj").get(0).eqw("no")) {
                clause.getVerb().setNegative(true);
            }

            process(sov);
            sort(sov);
            if (clause.getParent() != null) {
                sov.setParent(parent);
            }
            sov.setPerson(analyzer.identifyPerson(sov, main, parent));
        }
        sov.setOpening(clause.getOpening());
        sov.setClosing(clause.getClosing());

        return sov;
    }

    private void affectNegativeWords(SovClause clause) {
        affectNegativeWords(clause.words());
        clause.subs().forEach((k, v) -> {
            affectNegativeWords(v.words());
        });
    }

    private void affectNegativeWords(WordGroup wg) {
        for (int i = 0; i < wg.size(); i++) {
            if (wg.ml(i, "no|none|none of|nothing|nobody|no longer")) {
                if (i > 0) {
                    if (wg.get(i - 1).isVerb()) {
                        wg.get(i - 1).negate();
                    }
                } else {
                    int vi = wg.findVerbAfter(i);
                    if (vi != -1) { // Nobody agreed with me. // No humans are perfect.
                        wg.get(vi).negate();
                    }
                }
            } else if (wg.mw(i, "never")) { //|nor|neither
                int j = indexOfVerb(wg, i + 1);
                if (j != -1) {
                    wg.get(j).negate();
                }
            }
        }
    }

    public SovClause sort(SovClause sov) {
        WordGroup wg = sov.words();

        gramHow(wg);
        gramWould(wg);

        int i;
        for (i = 0; i < wg.size() - 1; i++) {
            if (wg.eqt(i, "NNP") && wg.get(i + 1).eqt("NNP") && !wg.get(i).matchw("Mr[s]?[\\.]?|Dr[\\.]?")) {
                wg.get(i).merge(wg.get(i + 1));
                wg.get(i + 1).disable();
            }
        }
        for (i = 0; i < wg.size() - 1; i++) {
            if (wg.eqw(i, "look") && wg.get(i + 1).eqw("like")) {
                wg.get(i + 1).setTag("RP");
//            } else if (safeGet(ps,i).equals("of")) {
//                ps.remove(i);
            }
        }

        for (i = 0; i < wg.size() - 1; i++) {
            if (wg.ml(i, "have|will|be|can|may") && wg.get(i + 1).eqt("RB") && !wg.get(i + 1).matchl("not|no longer")) { //ever,never,already,often
                if (wg.get(i + 1).eql("so") && wg.get(i + 2).matcht("JJR?")) {

                } else {
                    wg.add(i + 1, wg.remove(i)); //swap!
                }
            }
        }

        for (i = 0; i < wg.size() - 1; i++) {
            if (wg.get(i).eqw("ought") && wg.get(i + 1).eqw("to")) {
                wg.get(i + 1).disable();
            }
        }

        if (wg.get(0).eql("what")) {
            if (wg.get(1).eql("be")) {
                wg.get(0).merge(wg.get(1));
                wg.get(0).setTag(wg.get(1).tag());
                wg.get(0).setLemma(wg.get(1).lemma());
                wg.get(1).disable();
                sov.setIdxVerb(0);
                sov.setType(SentenceType.Interrogative);
            } else if (wg.get(1).lemma().equals("do")) {
                sov.setType(SentenceType.Interrogative);
                wg.get(1).disable();
            }
        }

        for (i = 1; i < wg.size(); i++) {
            if (wg.get(i).word().equals("in")) {
                if (wg.get(i - 1).lemma().equals("break")) {
                    wg.get(i).setTag("RP");
                }
            } else if (wg.get(i).lt().matches("['’]s?/POS")) {
                wg.get(i).disable();
                wg.get(i - 1).setTag(wg.get(i - 1).tag() + "_POS");
            }
        }

//        for (i = 1; i < ps.length(); i++) {
//            if (safeGet(ps,i).lemma().matcht("no|neither") && ps.get(i - 1).lemma().matcht("be|have")) {
//                ps.get(i - 1).negate();
//            }
//        }
        for (i = 1; i < wg.size() - 1; i++) {
            if (wg.get(i).eqw("more") && wg.get(i - 1).eqt("JJ")) {
                wg.add(i, wg.remove(i - 1));
            }
        }
        i = 0;
        while (i < wg.size() - 1) {
            if (!wg.get(i).isDisabled()) {
                if (wg.get(i).eqt("PRP$") || wg.get(i).tag().endsWith("_POS")) {
                    int j = i + 1;
                    while (j < wg.size() && (wg.get(j).matcht("NNS?") || wg.get(j).matcht("JJR?S?") || wg.get(j).eqt("RBS"))) {
                        j++;
                    }
                    wg.add(j - 1, wg.remove(i));
                } else if (wg.get(i).eqt("WP$")) {
                    wg.get(i).setTranslation("که");
                    int j = i + 1;
                    while (j < wg.size() && (wg.get(j).matcht("NN.*") || wg.get(j).matcht("JJR?S?") || wg.get(j).eqt("RBS"))) {
                        j++;
                    }
                    ExtWord its = new ExtWord("its", "", "its");
                    its.setTranslation("اش");
                    its.setClauseId(wg.get(i).getClauseId());
                    its.setDepth(wg.get(i).depth());
                    wg.add(j, its);
                    if (i < sov.getIdxVerb()) {
                        sov.setIdxVerb(sov.getIdxVerb() + 1);
                    }
                    i = j;

                }
            }
            i++;
        }

//        for (i = 1; i < ps.length(); i++) {
//            if (ps.eql(i, "not")) {
//                ExtWord tw = ps.get(i - 1);
//                if (tw.lemma().matches("do|be")) {
//                    if (tw.matchw("did|was|were")) {
//                        tense = Tense.Past_Simple;
//                    }
//                    if (i + 1 < ps.length() && ps.get(i + 1).tag().startsWith("VB")) {
//                        ps.get(i + 1).negate();
//                        ps.remove(i);
//                        ps.remove(i - 1);
//                    }
//                }
//            }
//        }
//        for (int i = 0; i < ps.length() - 1; i++) {
//            if (safeGet(ps,i).lemma().equals("have") && ps.get(i + 1).lemma().equals("ever")) {
//                ps.add(i + 1, ps.remove(i));
//            }
//        }
        if (sov.getIdxVerb() != -1) {
            reorderCombinations(wg, 0, sov.getIdxVerb() - 1);
            reorderCombinations(wg, sov.getIdxVerb() + 1, wg.size());
        } else {
            reorderCombinations(wg, 0, wg.size());
        }

        if (logs != null) {
            if (sov.getIdxVerb() != -1) {
                logs.add(sov.infoString() + "<span class='text-red-300' style='font-family:Kalam'> (" + sov.getType() + " - " + sov.tense().toString().replace("_", " ") + " - " + sov.voice() + ")</span>");
            } else {
                logs.add(sov.infoString());
            }
        }
        return sov;
    }

    private void reorderCombinations(WordGroup wg, int start, int end) {
        int c = wg.findWord("and|or|but", start, end);
        if (c != -1) {
            int p1 = wg.findTag("NNP?S?", start, c);
            int p2 = wg.findTag("NNP?S?", c + 1, end);
            if (p1 != -1 && p2 != -1) {
                reorderCombinations2(wg, start, c);
                reorderCombinations2(wg, c + 1, end);
            } else {
                reorderCombinations2(wg, start, end);
            }
        } else {
            reorderCombinations2(wg, start, end);
        }
    }

    private void reorderCombinations2(WordGroup wg, int start, int end) {

        int i = end;
        while (i > start) {
            if (wg.get(i).matcht("NNP?S?") && !"adv".equals(wg.get(i).partOfSentence())) {
                int j = i - 1;
                if (wg.get(j).isDisabled()) {
                    j--;
                }
                boolean b = wg.get(j).matcht("NNP?S?(_POS)?") || wg.get(j).matcht("CD") || (wg.get(j).matcht("JJR?") && !wg.get(j).matchw("many|multiple|whole|such|several|few|a few"));
                while (b && j > start && !"adv".equals(wg.get(i).partOfSentence()) 
                        && (wg.get(j - 1).isDisabled() || wg.get(j - 1).matcht("RB|NNP?S?|POS|NNP?_POS") || (wg.get(j - 1).matcht("JJR?") && !wg.get(j - 1).matchw("many|several|a number of")) || wg.mw(j - 1, "and|but"))) {
                    j--;
                }
                if (b) {
                    if (wg.mw(j, "and|but")) {
                        j++;
                    }
                    List<ExtWord> l = wg.subList(j, i);
                    List<ExtWord> reversed = new ArrayList();
                    int k = l.size() - 1;
                    while (k >= 0) {
                        reversed.add(l.get(k));
                        k--;
                        if (k >= 0 && l.get(k).eqt("RB")) {
                            reversed.add(reversed.size() - 1, l.get(k));
                            k--;
                        }
                    }
                    if (wg.get(i).getBrackets() != null && wg.get(i).getBrackets() != -1) {
                        reversed.get(reversed.size() - 1).setBrackets(wg.get(i).getBrackets());
                        wg.get(i).setBrackets(-1);
                    }

                    wg.addAll(i + 1, reversed);
                    for (k = 0; k < i - j; k++) {
                        wg.remove(j);
                    }
                    i = j;
                }
            }
            if (wg.get(i).word().equalsIgnoreCase("o'clock")) {
                if (wg.get(i - 1).eqt("CD")) {
                    wg.add(i - 1, wg.remove(i));
                    i--;
                }
            }
            i--;
        }
    }

    private void gramWould(WordGroup ps) {
        int i = 0;
        while (i < ps.size() - 1) {
            /* Yes, I’d like to.      */
            if (ps.get(i).eql("would") && ps.get(i + 2).eql("like")) {
                /* Would you like ...     */
                ps.get(i).setTranslation("آیا");
                ps.get(i).setTag("");
                ps.get(i + 2).setTranslation(",خواست,خواه");
                ps.get(i + 1).set(AzdegarAnnotations.PartOfSentenceAnnotation.class, "subj");
                return;
            } else if (ps.get(i + 1).matchl("would|'d")) {
                /* I Would like ...      */
                if (ps.get(i + 2).eql("like")) {
                    ps.get(i + 1).setTranslation("");
                    ps.get(i + 1).setTag("");
                    ps.get(i + 1).setLemma("");
                    ps.get(i + 2).setTranslation(",خواست,خواه");
                    ps.get(i).set(AzdegarAnnotations.PartOfSentenceAnnotation.class, "subj");
                } else if (ps.get(i + 3).eql("like") && ps.get(i + 2).eql("not")) {
                    ps.get(i + 1).setTranslation("");
                    ps.get(i + 1).setTag("");
                    ps.get(i + 1).setLemma("");
                    ps.get(i + 3).setTranslation(",خواست,خواه");
                    ps.get(i + 3).negate();
                    ps.get(i).set(AzdegarAnnotations.PartOfSentenceAnnotation.class, "subj");

                }
                return;
            }
            i++;
        }
    }

    private void gramPrefer(SovClause sov) {
        List<ExtWord> in = sov.getWords();
        int begin = 0;
        if (in.get(0).eql("would")) {
            sov.setType(SentenceType.Interrogative);
            begin = 1;
        }
        for (int i = begin; i < in.size(); i++) {
            if (in.get(i).eql("prefer") && in.get(i).isVerb()) {
                int end = i;
                if (i > 0 && in.get(i - 1).eql("would")) {
                    end--;
                    in.remove(i - 1);
                }
                for (int j = begin; j < end; j++) {
                    in.get(j).set(AzdegarAnnotations.PartOfSentenceAnnotation.class, "subj");
                }
                for (int j = end + 1; j < in.size(); j++) {
                    if (!in.get(j).word().equals(".")) {
                        in.get(j).set(AzdegarAnnotations.PartOfSentenceAnnotation.class, "dobj");
                    }
                }
            }
        }
        for (int i = begin; i < in.size(); i++) {
            if (in.get(i).eql("prefer") && in.get(i).isVerb()) {
                in.get(i).set(AzdegarAnnotations.PartOfSentenceAnnotation.class, "verb");
                copyToEnd(i, in);
            }
        }
    }

    private int copyToEnd(int idx, List<ExtWord> l) {
        if (l == null || l.isEmpty() || idx < 0 || idx > l.size() - 1) {
            return 0;
        }
        int last = l.size() - 1;
        while (last > 0 && (l.get(last).matcht(".|,|-RRB-") || l.get(last).isPlaceHolder() || l.get(last).matchw("then"))) {
            last--;
        }
        if (l.get(last+1).isPlaceHolder() && l.get(last).eqw("by")) { // 𝑯𝒆 𝒗𝒆𝒏𝒕𝒆𝒅 𝒉𝒊𝒔 𝒂𝒏𝒈𝒆𝒓 𝒃𝒚 𝒌𝒊𝒄𝒌𝒊𝒏𝒈 𝒕𝒉𝒆 𝒅𝒐𝒐𝒓.
            last++;
        }
        if (last == -1) {
            last = l.size() - 1;
        }
        if (last == idx + 1 && l.get(last).eqt("IN")) { // 𝑺𝒕𝒂𝒓𝒕 𝒃𝒚 𝒂𝒅𝒅𝒊𝒏𝒈 𝒂 𝒏𝒆𝒘 𝒄𝒐𝒏𝒏𝒆𝒄𝒕𝒊𝒗𝒆 𝒕𝒐 𝒕𝒉𝒆 𝒍𝒂𝒏𝒈𝒖𝒂𝒈𝒆.
            return idx;
        }

        if (idx != last) {
            ExtWord copy = new ExtWord(l.get(idx));
            copy.setNegative(l.get(idx).isNegative());
            if (l.get(idx).partOfSentence() != null) {
                copy.set(AzdegarAnnotations.PartOfSentenceAnnotation.class, l.get(idx).partOfSentence());
            }
            if (l.get(idx).otherVerb() != null) {
                copy.set(AzdegarAnnotations.MultiVerbAnnotation.class, l.get(idx).otherVerb());
            }
            copy.setIndex(l.get(idx).index());
            copy.setLocalIndex(last + 1);

            for (int i = idx + 1; i < l.size(); i++) {
                if (l.get(i).getBrackets() != null) {
                    if (i == l.size() - 1) {
                        copy.setBrackets(l.get(i).getBrackets());
                        l.get(i).setBrackets(null);
                    }
                }
            }
            l.get(idx).disable();
            l.add(last + 1, copy);
        }
        return last;
    }

    private int moveToEnd(int idx, List<ExtWord> l) {
        if (l == null || l.isEmpty() || idx < 0 || idx > l.size() - 1) {
            return 0;
        }
        int last = l.size() - 1;
        while (last > 0 && (l.get(last).matcht(".|,|-RRB-") || l.get(last).isPlaceHolder())) {
            last--;
        }

        if (last == -1) {
            last = l.size() - 1;
        }
        if (last == idx + 1 && l.get(last).eqt("IN")) { // 𝑺𝒕𝒂𝒓𝒕 𝒃𝒚 𝒂𝒅𝒅𝒊𝒏𝒈 𝒂 𝒏𝒆𝒘 𝒄𝒐𝒏𝒏𝒆𝒄𝒕𝒊𝒗𝒆 𝒕𝒐 𝒕𝒉𝒆 𝒍𝒂𝒏𝒈𝒖𝒂𝒈𝒆.
            return idx;
        }
        if (idx != last) {
            l.add(last, l.remove(idx));
        }
        return last;
    }

    public void process(SovClause sov) {

        int idx = sov.indexOf("prefer");
        if (idx != -1 && sov.get(idx).isVerb()) {
            gramPrefer(sov);
            return;
        }

        WordGroup wg = sov.getWords();

        affectNegativeWords(sov);

        /*
        int s = wg.findPartOfSentence("predicate", 1, sov.length());
        if (s != -1) {
            int i = s;
            int n = 0;
            while ("predicate".equals(sov.get(i).partOfSentence())) {
                n++;
                i++;
            }
            for (int j = 0; j < n; j++) {
                moveToEnd(s, wg);
            }
        }
*/
        if (sov.length() == 2) {
            if (sov.get(0).isVerb()) {
                if (sov.get(1).eqw("that")) {
                    sov.get(1).setTranslation("که");
                    return;
                }
            }
        }

        if (!sov.getVerb().eqt("INF") && !sov.getVerb().word().equals("See") && !sov.getVerb().matchl("want")) {
            copyToEnd(sov.getIdxVerb(), wg);
        }

        move_TOO(sov);

    }

    private void gramHow(WordGroup ps) {
        int i = 0;
        while (i < ps.size() - 1) {
            if (ps.eqw(i, "how")) {
                if (ps.get(i + 1).matchl("be|do|can")) {
                    ps.get(i).setSense(1);
                } else if (ps.get(i + 1).matcht("JJ|RB")) {
                    ps.get(i).setSense(2);
                }
            }
            i++;
        }
    }

    private int indexOfVerb(List<ExtWord> tokens, int begin) {
        int ret = -1;
        for (int i = begin; i < tokens.size(); i++) {
            if (tokens.get(i).tag().startsWith("VB")) {
                ret = i;
                if (!tokens.get(i).isAux()) {
                    return i;
                }
            }
        }
        return ret;
    }


    /*
    private void identifyObjectBak(SovClause sov) {
        if (sov.getIdxVerb() == -1 || sov.getIdxVerb() == sov.length() - 1) {
            return;
        }
        int idx1 = sov.isActive() ? sov.getIdxVerb() + 1 : sov.getEffectiveStart().index();
        int i = idx1;
        int e = idx1;
        while (i < sov.length()) {
            if (sov.get(i).startt("NN") || sov.get(i).matcht("DT|PRP")) {
                if (i == 0 || !sov.get(i - 1).word().equalsIgnoreCase("as")) {
                    e = i;
                    break;
                }
            }
            i++;
        }
        if (i == sov.length()) {
            return;
        }
        while (e > 0 && e >= idx1 && sov.get(e - 1).matcht("JJR?|DT")) {
            e--;
        }
        idx1 = e;
        int phrase = sov.get(idx1).phraseId();
        boolean vt = false;
        if (phrase != -1) {
            i = idx1;
            int idx2 = idx1;
            boolean pp = false;
            while (i < sov.length()) {
                while (i + 1 < sov.length()) {
                    if (sov.get(i + 1).phraseId() == phrase) {
                        idx2++;
                        i++;
                    } else {
//                            if (!sov.get(i + 1).matcht(".|,")) {
                        if (i + 1 != sov.length() - 1) {
                            vt = true;
                        }
                        i++;
                    }
                }
                if (sov.get(i).phraseType().equals("PP")) {
                    pp = true;
                }
                i++;
            }
            if (pp) {
                sov.setIdxDobj(idx1);
                if (idx2 != idx1) {
                    sov.setIdxIobj(idx2);
                    for (i = idx1; i < idx2; i++) {
                        sov.get(i).set(AzdegarAnnotations.PartOfSentenceAnnotation.class, "dobj");
                    }
                    for (i = idx2; i < sov.length(); i++) {
                        sov.get(i).set(AzdegarAnnotations.PartOfSentenceAnnotation.class, "iobj");
                    }
                } else {
                    i = idx1;
                    while (i < sov.length() && !sov.get(i).isVerb() && (!sov.get(i).matcht("RB|IN|,") || sov.get(i).eql("of"))) {
                        sov.get(i).set(AzdegarAnnotations.PartOfSentenceAnnotation.class, "dobj");
                        i++;
                    }

                }
            } else {
                if (idx2 == idx1) {
                    if (vt) {
                        sov.setIdxDobj(idx2 + 1);
                        sov.setIdxIobj(idx1);
                    } else {
                        sov.setIdxDobj(idx1);
                        sov.setIdxIobj(-1);
                    }
                }
                if (idx2 != sov.length() - 1) {
                    if (vt) {
                        sov.setIdxDobj(idx2 + 1);
                        sov.setIdxIobj(idx1);
                    }
                }

                if (sov.getIdxIobj() != -1) {
                    for (i = sov.getIdxIobj(); i < sov.getIdxDobj(); i++) {
                        sov.get(i).set(AzdegarAnnotations.PartOfSentenceAnnotation.class, "iobj");
                    }
                }
                for (i = sov.getIdxDobj(); i < sov.length(); i++) {
                    if (!sov.get(i).eqt(".")) {
                        if (!sov.get(i).tag().startsWith("VB") && (!sov.get(i).matcht("RB|IN|,") || sov.get(i).eql("of"))) {
                            sov.get(i).set(AzdegarAnnotations.PartOfSentenceAnnotation.class, "dobj");
                        }
                    } else {
                        sov.get(i).set(AzdegarAnnotations.PartOfSentenceAnnotation.class, "punc");
                    }
                }
            }
        }

    }
     */
    public List<String> getLogs() {
        return logs;
    }

    private void move_TOO(SovClause sov) {
        int i = sov.size() - 1;
        while (sov.get(i).matcht("\\.|VB[DPZ]?")) {
            i--;
        }
        if (sov.get(i).eqlt("too/RB")) {
            int j = sov.partEndIndex("subj");
            if (j != -1) {
                sov.move(i, j + 1);
            }
        }
    }

}
