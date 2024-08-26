package com.azdegar.translator;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.PrintWriter;
import static java.lang.Math.max;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import edu.stanford.nlp.ling.CoreLabel;
import com.azdegar.nlp.EnglishUtils;
import com.azdegar.nlp.SentenceType;
import com.azdegar.nlp.Voice;
import com.azdegar.nlp.Tense;
import com.azdegar.nlp.Parser;
import com.azdegar.nlp.EnglishNoun;
import com.azdegar.nlp.Clause;
import com.azdegar.nlp.ExtWord;
import com.azdegar.nlp.WordGroup;
import com.azdegar.translator.model.Translation;
import com.azdegar.nlp.TypeRepository;
import com.azdegar.translator.transform.One;
import com.azdegar.translator.transform.Transformable;

import static com.azdegar.nlp.EnglishGrammar.GRAM_TO;
import com.azdegar.nlp.OntologyRepository;
import static com.azdegar.nlp.Parser.CLAUSE_PLACEHOLDER;
import static com.azdegar.util.StringUtils.clean;

/**
 *
 * @author Afshin Pouria
 */
public class PersianTranslator {

    private final Parser parser;
    private final EntityManager em;
    private final static ExtWord newline;
    private final static ExtWord space;
    private final static ExtWord halfSpace;
    private String lang = "PA";

    static {
        newline = new ExtWord("<br/>", "NL", "<br/>");
        newline.setTranslation("<br/>");
        space = new ExtWord(" ", "", "");
        space.setTranslation(" ");
        halfSpace = new ExtWord(" ", "", "");
        halfSpace.setTranslation("‌");
    }

    public PersianTranslator(Parser parser, EntityManager em) {
        this.parser = parser;
        this.em = em;
    }

    public List<ExtWord> translateString(String text, String domain, OntologyRepository ontologyRepository, TypeRepository typeRepository, List<String> logs) throws Exception {
        List<ExtWord> ret = new ArrayList();

        Set<String> distinctWords = new TreeSet();
        String[] paragraphs = text.split("\n");
        for (int k = 0; k < paragraphs.length; k++) {
            String paragraph = paragraphs[k].replaceAll("\\s+", " ");
            if (paragraph.trim().isBlank()) {
                continue;
            }

            String[] split = paragraph.split("[\\s\\.\",?!;:—\\(\\)⟨⟩“”‘’]");
            for (int i = 0; i < split.length; i++) {
                if (split[i].isBlank()) {
                    continue;
                }
                String s = split[i];
                if (s.endsWith("n't")) {
                    s = s.substring(0, s.length() - 3);
//                } else if (s.toLowerCase().matches("ha[ve|s|d]")) {
//                    if (i < split.length - 1 && split[i + 1].equalsIgnoreCase("to")) {
//                        distinctWords.add("have to");
//                    }
//                } else if (s.toLowerCase().startsWith("there")) { //There's a radically indeterminacy about the future.
//                    distinctWords.add("exist");
                }
                if (shouldTranslate(s)) {
                    distinctWords.add(s);
                    String lower = s.toLowerCase();
                    if (!lower.equals(s)) {
                        distinctWords.add(lower);
                    }
                    if (EnglishNoun.isPlural(s)) {
                        distinctWords.add(EnglishNoun.singularOf(s));
                    }
                    if (s.matches("^[A-Z][a-z]+")) { // Concat many proper names
                        int j = i + 1;
                        String t = s;
                        while (j < split.length && split[j].matches("^[A-Z][a-z]+")) {
                            t += " " + split[j];
                            distinctWords.add(split[j]);
                            distinctWords.add(t);
                            j++;
                        }
                    }
                    if (s.contains("-")) { // self-confident
                        distinctWords.addAll(Arrays.asList(s.split("-")));
                    }
                    if (s.endsWith("er")) {
                        int j = s.lastIndexOf("er");
                        distinctWords.add(s.substring(0, j));

                    } else if (s.endsWith("est")) {
                        int j = s.lastIndexOf("est");
                        distinctWords.add(s.substring(0, j));

                    }
                }
            }
        }

        Query q = em.createNamedQuery("Translation.select");

        for (String paragraph : paragraphs) {
            if (paragraph.trim().isBlank()) {
                ret.add(newline);
                continue;
            }
            paragraph = paragraph.replaceFirst("((\\d\\.)+\\d)\\s+([A-Z].+)", "$1##$3"); //10.7.3 Start by adding ...
//            paragraph = paragraph.replace("that is,", "##that is,##").replace("—", "##—##");
            String[] parts = paragraph.split("##");
            SovService sovService = new SovService(logs);
            for (String part : parts) {
                Map<Integer, Clause> clauses = parser.parse(part, logs);

                clauses.forEach((key, main) -> {
                    parser.processPhrasal(main.getWords());
                    main.getWords().forEach(tw -> {
                        addWordToSet(distinctWords, tw);
                    });
                    for (Clause sub : main.getSubs().values()) {
                        parser.processPhrasal(sub.getWords());
                        sub.getWords().forEach(tw -> {
                            addWordToSet(distinctWords, tw);
                        });
                    }
                });
                q.setParameter("english", distinctWords);
                List<Translation> dbResults = q.getResultList();
                Map<String, SovClause> parentMap = new LinkedHashMap();
                clauses.forEach((key, main) -> {
                    instantTrans(main.words(), parser.getLemmatized());
                    SovClause parent = null;
                    if (main.getParent() != null) {
                        parent = parentMap.get(main.getParent().getKey());
                    }
                    SovClause sovm = sovService.build(main, null, parent, parser.getLemmatized(), typeRepository);
                    parentMap.put(main.getKey(), sovm);
                    trans(sovm, dbResults, domain, ontologyRepository, parser.getLemmatized());

                    for (Clause sub : main.getSubs().values()) {
                        instantTrans(sub.words(), parser.getLemmatized());
                        SovClause sov = sovService.build(sub, main, sovm, parser.getLemmatized(), typeRepository);
                        ExtWord then = sov.returnIfStartsWith("then");
                        if (then != null) {
                            if (sovm.isIfStmt()) {
                                then.setTranslation("آنگاه");
                            }
                        }
                        trans(sov, dbResults, domain, ontologyRepository, parser.getLemmatized());
                    }
                });

                clauses.forEach((key, main) -> {
                    if (!main.isMarked()) {
                        insertBrackets(main, clauses, key);
                        for (Clause sub : main.subs().values()) {
                            if (!sub.isMarked()) {
                                insertBrackets(sub, clauses, key);
                                sub.setMarked(true);
                            }
                        }
                        ret.addAll(main.getMerged());
                        main.setMarked(true);
                    }
                });

            }
            ret.add(newline);
        }

        return joinery(ret);
    }

    private void insertBrackets(Clause clause, Map<Integer, Clause> clauses, Integer key) {
        for (int i = 0; i < clause.words().size(); i++) {
            Integer brackets = clause.words().get(i).getBrackets();
            if (brackets != null && !brackets.equals(key) && clauses.get(brackets) != null) {
                WordGroup merged = new WordGroup(clauses.get(brackets).getMerged());
                clauses.get(brackets).setMarked(true);
                int k = 1;
                while (clauses.get(brackets + k) != null) {
                    merged.addAll(clauses.get(brackets + k).getMerged());
                    clauses.get(brackets + k).setMarked(true);
                    k++;
                }
                clause.words().addAll(i + 1, merged);
            }
        }

    }

    private void instantTrans(WordGroup words, List<CoreLabel> whole) {
        int last = max(0, words.size() - 1);
        if (words.eqt(last, ".") && words.size() > 1) {
            last--;
        }

//        if (words.eql(last, "so")) {
//            words.get(last).setTranslation("اینگونه");
//        } else if (words.eql(last, "to")) {
//            words.get(last).setTranslation("هم");
//        } else if (words.eql(last, "since")) {
//            words.get(last).setTranslation("از آن زمان");
//        }
        ExtWord start = words.getEffectiveStart();
        if (start.eql("of")) {
            start.setTranslation("از");
        }

        for (int i = 0; i < words.size(); i++) {
            ExtWord word = words.get(i);
            if (word.eqw("some")) {
                if (words.eqw(i + 1, "of")) {
                    word.setTranslation("برخی");
                    words.get(i + 1).setTranslation("از");
                } else if (words.mt(i + 1, "NNP?S")) {
                    word.setTranslation("برخی");
                }
            } else if (words.eql(i, "to")) {
                if (words.eqw(i + 1, "be")) {
                    word.disable();
                } else if (word.index() > 0 && word.index() - 1 < whole.size() && whole.get(word.index() - 1).lemma().matches(GRAM_TO)) {
                    word.disable();
                }
            } else if (word.eqlt("when/WRB")) {
                if (!words.get(i + 1).matchl("do|be|can|will|should")) {
                    word.setTag("IN");
                }
            } else if (i > 1 && words.get(i).eqlt("as/RB")) {
                if (words.get(i - 1).matcht("JJR?")) {
                    words.get(i).setTranslation("ی");
                }
            } else if (word.matchw("[\\d\\.\\-,–]+[th]*")) {
                word.setTranslation(clean(word.word()).replace("th", "ام"));
                continue;
            } else if (word.matcht("SYM|NFP")) {
                if (word.word() != null) {
                    word.setTranslation(word.word());
                }
            } else if (word.eqwt("'/POS")) {
                word.setTranslation("");
                word.disable();
            } else if (word.eqw("by")) {
                if (i + 1 < words.size()) {
                    if (words.get(i + 1).matcht("NNPS?") || words.get(i - 1).isVerb()) {
                        if (lang.matches("P[AS]")) {
                            word.setTranslation("بدست");
                        } else {
                            word.setTranslation("بوسیله");
                        }
                    } else {
                        word.setTranslation("با");
                    }
                } else {
                    word.setTranslation("با");
                }

            } else if (word.eqw("I")) {
                word.setTranslation("من");
                if (i + 1 < words.size()) {
                    if (words.get(i + 1).matchw("is|has|does")) {
                        word.setTranslation("I");
                    }
                }
            } else if (word.eql("if")) {
                int then = words.findWord("then", i + 1, words.size());
                if (then != -1) {
                    words.get(then).setTranslation("آنگاه");
                }
            } else if (word.matchw("M{0,4}(CM|CD|D?C{0,3})(XC|XL|L?X{0,3})(IX|IV|V?I{0,3})")) {
                word.setTranslation(word.word());
            }
            int j = 0;
            while (j < quicktrans.length) {
                if (quicktrans[j][0].equalsIgnoreCase(word.word())) {
                    word.setTranslation(quicktrans[j][1]);
                    break;
                }
                j++;
            }
        }
//        echoTime("InstantTrans", t1);
    }

    private void changeToPlural(ExtWord word, ExtWord prev, ExtWord next) {
        if (word.matchw("people") || prev.matchw("several|few|a few|no") || prev.eqt("CD")) {
            return;
        }
        if (word.translation().endsWith("ها") || word.translation().contains("ها ") || word.translation().contains("های ")) {
            return;
        }
        int i = word.translation().indexOf("/");
        if (i != -1) {
            word.setTranslation(word.translation().substring(i + 1));  //      یونان/یونانیان
        } else {
            word.setTranslation(word.translation() + "‌ها");
        }
        if (word.getTranslations() != null && !word.getTranslations().isEmpty()) {
            for (Object o : word.getTranslations()) {
                Translation t = (Translation) o;
                i = t.parsi().indexOf("/");
                if (i != -1) {
                    t.setParsi(t.parsi().substring(i + 1));
                }
                if (t.farsi() != null && !t.farsi().isBlank()) {
                    i = t.farsi().indexOf("/");
                    if (i != -1) {
                        t.setFarsi(t.farsi().substring(i + 1));
                    }
                }
            }
        }
        word.setExactTrans(true);

        if (word.translation().endsWith("‌ها")) {
            if (next != null) {
                if (next.matcht("JJ|NN") || next.eql("of")) { // process variables
                    word.setTranslation(word.translation() + "ی");
                }
            }
        }
    }

    private void trans(SovClause sov, List<Translation> dbResults, String domain, OntologyRepository ontologyRepository, List<CoreLabel> whole) {
        long t1 = System.nanoTime();
        transform(sov.words());
        if (sov.getVerb().otherVerb() != null) {
            ExtWord v = sov.findMovedVerb();
            int k = v.getLocalIndex();
            if (k >= sov.size()) {
                k = sov.size() - 1;
            }
            for (CoreLabel c : v.otherVerb()) {
                k++;
                sov.words().add(k, new ExtWord(c));
            }
        }

        for (int i = 0; i < sov.getWords().size(); i++) {
            ExtWord word = sov.getWords().get(i);
            if (word.isDisabled() || word.matchl(CLAUSE_PLACEHOLDER)) {
                continue;
            }
            if (word.translation() != null && !word.getTranslation().contains(",")) {
                continue;
            }
            if (word.eqw("the")) {
                continue;
            }
            if (word.eqw("of")) {
                if (i > 0) {
                    if (sov.get(i - 1).eql("think")) { // disabled verb at original index
                        word.setTranslation("به");
                        continue;
                    } else if (sov.get(i - 1).eql("make")) {
                        word.setTranslation("از");
                        continue;
                    }
                }
                continue;
            }
            List<Translation> l = new LinkedList();
            dbResults.forEach(t -> {
                String lemma = word.lemma();
                if (lemma != null) {
                    int ri = lemma.indexOf("⇒");
                    if (ri != -1) {
                        lemma = lemma.substring(0, ri);
                    }
                }
                if (word.eqw(t.english()) || (lemma != null && !word.matcht("JJ[RS]") && lemma.equals(t.english())) || word.singular().equalsIgnoreCase(t.english())) {
                    if (!l.contains(t)) {
                        l.add(t);
                    }
                }
            });
//            if (l.isEmpty()) {
//                for (Translation t : dbResults) {
//                    if (word.eql(t.getEnglish())) {
//                        l.add(t);
//                    } else if (word.lemma().compareTo(t.getEnglish()) < 0) {
//                        break;
//                    }
//                }
//            }
            if (l.size() == 1) {
                word.setTranslation(l.get(0).getTranslation(lang), l.get(0).getTransitive());
                if (word.eql("be")) {
                    translateBe(word, sov, i);
                }
                if (word.eqt(l.get(0).getPos())) {
                    word.setExactTrans(true);
                }

            } else if (!l.isEmpty()) {
                List<Translation> lt = new LinkedList();
                String tag = word.tag();
                if (!word.matcht("NNP?S")) {
                    if (tag.matches("VB[DGPZ]?") || tag.equals("INF") || (tag.equals("VBN") && (sov.getTense().toString().contains("Perfect") || sov.getVoice() == Voice.Passive))) {
                        tag = "VB";
                    }
                    if (word.isCausative()) {
                        tag = "VBC";
                    }
                }
                for (Translation t : l) {
                    if (tag.equals(t.getPos()) || (tag.equals("NNS") && t.getPos().equals("NN"))) {
                        lt.add(t);
                    } else if (word.eqt("NN") && word.matchw("[A-Z][a-z]+")) {
                        lt.add(t);
                    }
                }
                if (lt.isEmpty()) {
                    List<Translation> filtered = l.stream().filter(e -> word.tag().startsWith(e.getPos())).toList(); // accept VBZ for VB
                    if (filtered.isEmpty()) {
                        filtered = l;
                    }
                    word.setTranslations(filtered);
                    findBestMeaning(word, sov, domain, ontologyRepository, whole);
                } else if (lt.size() == 1) {
                    word.setTranslation(lt.get(0).getTranslation(lang), lt.get(0).getTransitive());
                    if (word.eqt(lt.get(0).getPos())) {
                        word.setExactTrans(true);
                    }

                } else {
                    if (word.eql("be")) {
                        translateBe(word, sov, i);
                    } else {
                        word.setTranslations(lt);
                        findBestMeaning(word, sov, domain, ontologyRepository, whole);
                        Set<Translation> ts = new TreeSet();
                        for (Translation t : lt) {
                            boolean b = true;
                            for (Translation a : ts) {
                                if (t.getTranslation(lang) != null && t.getTranslation(lang).equals(a.getTranslation(lang))) {
                                    b = false;
                                }
                            }
                            if (b) {
                                ts.add(t);
                            }
                        }
                        if (ts.size() > 1) {
                            word.setTranslations(new ArrayList(ts));
                        }
                    }
                }
            }

            if (word.translation() == null) {
                if (!word.word().contains("-")) {
                    word.setTranslation(word.word());
                } else {
                    String[] ps = word.word().split("-");
                    List<String> ts = new ArrayList(ps.length);
                    for (int j = 0; j < ps.length; j++) {
                        if (j == ps.length - 1 && ps[j].equalsIgnoreCase("like")) {
                            ts.add("مانند");
                        } else if (ps[j].equalsIgnoreCase("non")) {
                            ts.add("نا");
                        } else {
                            for (Translation t : dbResults) {
                                if (ps[j].equalsIgnoreCase(t.getEnglish())) {
                                    ts.add(t.getParsi());
                                    break;
                                }
                            }
                            if (ts.size() <= j) {
                                ts.add(ps[j]);
                            }
                        }
                    }
                    word.setTranslation(ts.stream().collect(Collectors.joining("-")));
                }
            }
            if (word.eqt("JJ") && sov.get(i - 1).eqw("most")) {
                if (word.getTranslation() != null && !word.translation().isBlank()) {
                    word.setTranslation(word.getTranslation() + "ترین");
                    sov.get(i - 1).disable();
                }
            }
            if (word.translation() == null || word.translation().equals(word.word())) {
                if (word.word().endsWith("er")) {
                    int j = word.word().lastIndexOf("er");
                    String stem = word.word().substring(0, j);
                    for (Translation t : dbResults) {
                        if (stem.equalsIgnoreCase(t.getEnglish())) {
                            String[] ps = t.getParsi().split(",");
                            String suffix = word.eqt("JJR") ? "تر" : "گر";
                            int k = 0;
                            if (ps[0].isBlank() && ps.length > 2) {
                                k = 2;
                            }
                            word.setTranslation(ps[k] + suffix);
                            break;
                        }
                    }
                } else if (word.eqt("JJS") && word.word().endsWith("est")) {
                    int j = word.word().lastIndexOf("est");
                    String stem = word.word().substring(0, j);
                    for (Translation t : dbResults) {
                        if (stem.equalsIgnoreCase(t.getEnglish())) {
                            String[] ps = t.getParsi().split(",");
                            String suffix = "ترین";
                            word.setTranslation(ps[0] + suffix);
                            break;
                        }
                    }
                }
            }
            if (word.matcht("NNP?S") && !word.isExactTrans()) {
                changeToPlural(word, sov.prev(i), sov.next(i));

            } else if (word.matcht("NNP?|JJ")) { // Greek/JJ astronomers
                int si = word.translation().indexOf("/");
                if (si != -1) {
                    word.setTranslation(word.translation().substring(0, si));
                }
                if (word.getTranslations() != null && !word.getTranslations().isEmpty()) {
                    for (Object o : word.getTranslations()) {
                        Translation t = (Translation) o;
                        si = t.parsi().indexOf("/");
                        if (si != -1) {
                            t.setParsi(t.parsi().substring(0, si));
                        }
                        if (t.farsi() != null && !t.farsi().isBlank()) {
                            si = t.farsi().indexOf("/");
                            if (si != -1) {
                                t.setFarsi(t.farsi().substring(0, si));
                            }

                        }
                    }
                }
            }

            if (!word.eqw(word.translation())) {
                if (word.isVerb()) {
                    if (word.eqt("VBG") && word.index() != sov.getIdxVerb()) {
                        word.setTranslation(PersianInflector.infintive(word, sov));
                    } else {
                        word.setTranslation(PersianInflector.inflect(sov, i));
                    }
                } else if (word.matchl("can|may|have to|be to|use to|be going to|must")) {
                    word.setTranslation(PersianInflector.inflectModal(sov, word));
                } else if (word.eqlt("need/VBZ?")) {
                    word.setTranslation(PersianInflector.inflectModal(sov, word));
                } else if (word.tag().matches("INF") || sov.getTense() == Tense.NONE) {
                    word.setTranslation(PersianInflector.infintive(word, sov));
                } else if (word.tag().matches("JJG")) {
                    word.setTranslation(PersianInflector.infintive(word, sov));
                } else if (word.translation().contains(",")) {
                    if (word.eqt("VBN")) {
                        word.setTranslation(PersianInflector.participle(word));
                    } else {
                        word.setTranslation(PersianInflector.infintive(word, sov));
                    }
                } else if (word.eqlt("would/MD")) {
                    if (word.isNegative()) {
                        word.setTranslation("ن" + word.getTranslation());
                    }
                }
            }
            if (word.eqw(word.translation()) && word.tag().startsWith("NNP") && word.word().matches("[A-Z][a-zs]+(\\s[A-Z][a-zs]+)*")) {
//                    if (word.word().contains(" ")) {
                String[] parts = word.word().split(" ");
                String trans = word.word();
                for (String part : parts) {
                    boolean b = false;
                    for (Translation t : dbResults) {
                        if (part.equals(t.getEnglish())) {
                            trans = trans.replace(part, t.getParsi());
                            b = true;
                            break;
                        }
                    }
                    if (!b) {
                        trans = trans.replace(part, verbal(part));
                    }
                }
                word.setTranslation(trans);
            }
            if (word.eqw("should")) {
                if (word.isNegative()) {
                    word.setTranslation("ن" + word.translation());
                }
            } else if (word.matchw("any(thing|body|one)?")) {
                if (word.isNegative() || sov.words().findWord("not", i - 3, i - 1) != -1 || sov.getType().equals(SentenceType.Interrogative)) {
                    word.setTranslation(word.translation().replace("هر", "هیچ‌"));
                    sov.words().get(i + 1).setUnspecified(true);
                } else {
                    word.setTranslation(word.translation().replace("هیچ", "هر"));
                }
            }
            if (word.isUnspecified() && !word.isVerb()) {
                if (word.translation().endsWith("ه")) {
                    word.setTranslation(word.translation() + "‌ای");
                } else {
                    word.setTranslation(word.translation() + "ی");
                }
            }
            if (sov.next(i).tag().endsWith("POS")) {
                if (word.getTranslation().endsWith("ا") || word.getTranslation().endsWith("و")) {
                    word.setTranslation(word.getTranslation() + "ی");
                }
            }
//            if (word.getTranslations() != null && word.getTranslations().size() > 1) {
//                String prev = "";
//                Iterator<Translation> it = word.getTranslations().iterator();
//                while (it.hasNext()) {
//                    Translation next = it.next();
//                    if (next.getParsi().equals(prev)) {
//                        it.remove();
//                    } else {
//                        prev = next.getParsi();
//                    }
//                }
//            }
            if (word.getTranslations() != null && word.getTranslations().size() == 1) {
                word.setTranslations(null);
            }
        }
        ExtWord verb = sov.findMovedVerb();
        if (verb.getPreposition() != null && !verb.getPreposition().isBlank()) {
            if (!sov.isPassive()) {
                if (sov.partIndex("dobj") > sov.getIdxVerb()) {
                    ExtWord pre = new ExtWord(verb.getPreposition(), "PRP", "prp");
                    pre.setPersianOnly(true);
                    String p = verb.getPreposition();
                    if (p.contains("-")) {
                        p = p.split("-")[0];
                        pre.setWord(p);
                    }
                    pre.setTranslation(p);
                    int i = sov.partIndex("dobj");
                    int d = sov.get(i).depth();
//                    while (i < sov.length() && sov.get(i).partOfSentence() != null && sov.get(i).partOfSentence().equals("dobj") && (!sov.get(i).matcht("RB|IN|,") || sov.get(i).eql("of")) && sov.get(i).depth() >= d) {
                    while (i < sov.length() && sov.get(i).partOfSentence() != null && sov.get(i).partOfSentence().equals("dobj") && (sov.get(i).depth() >= d || sov.get(i).isJunctor())) {
                        i++;
                    }
                    sov.words().add(i, pre);
                    p = verb.getPreposition();
                    if (sov.partIndex("iobj") > 1 && p.contains("-")) {
                        p = p.split("-")[1];
                        pre = new ExtWord(p, "PRP", "prp");
                        pre.setTranslation(p);
                        sov.words().add(sov.partIndex("iobj") - 1, pre);
                    }
                }
            }
        }
        if (sov.getType() == SentenceType.Interrogative) {
            if (verb.matchl("be")) {
                ExtWord start = sov.getEffectiveStart();
                if (start.word().toLowerCase().matches("what|who|where")) {
                    sov.words().add(verb.getLocalIndex(), sov.words().remove(start.getLocalIndex()));
                }
            }
        }
        echoTime("Trans", t1);
    }

    private void findBestMeaning(ExtWord word, SovClause clause, String domain, OntologyRepository ontologyRepository, List<CoreLabel> adjacents) {
        int weight = -1;
        List<Translation> meanings = (List<Translation>) word.getTranslations();

        if (word.isVerb() && clause.getDirectObject() != null) {
            WordGroup directObject = clause.getDirectObject();
            int i = directObject.size() - 1;
            ExtWord dobj;
            while (i >= 0 && !directObject.get(i).matcht("NNS?")) {
                i--;
            }
            if (i > 0) {
                dobj = directObject.get(i);
                Character onto = ontologyRepository.find(EnglishNoun.singularOf(dobj.word()));
                if (onto != null) {
                    word.getTranslations().removeIf(e -> {
                        return !onto.equals(((Translation) e).getOntology());
                    });
                }
            }
        }
        for (Translation t : meanings) {
            if (!t.getDomain().equals("0") && t.getDomain().equals(domain)) {
                if (t.getWeightR() > weight) {
                    word.setTranslation(t.getTranslation(lang), meanings.get(0).getTransitive());
                    weight = t.getWeightR();
                }
            }
        }
        if (word.getTranslation() == null || word.getTranslation().isBlank()) {
            for (Translation t : meanings) {
                if (t.getWeight() > weight) {
                    word.setTranslation(t.getTranslation(lang), meanings.get(0).getTransitive());
                    weight = t.getWeight();
                }
            }
        }

        int distance = 1;
        while (distance < 4) {
            int index = word.index() + distance;
            if (index < adjacents.size()) {
                for (Translation meaning : meanings) {
                    if (meaning.getAux() != null && !meaning.getAux().isBlank()) {
                        String[] parts = meaning.getAux().split(",");
                        if (index < adjacents.size() && (adjacents.get(index).lemma().matches(parts[0]) || adjacents.get(index).tag().matches(parts[0]))) {
                            word.setTranslation(meaning.getTranslation(lang), meaning.getTransitive());
                            return;
                        }
                    }
                }
            } else {
                distance = 4;
            }
            distance++;
        }
        distance = 1;
        while (distance < 4) {
            int index = word.index() + distance * -1;
            if (index > 0) {
                for (Translation meaning : meanings) {
                    if (meaning.getAux() != null && !meaning.getAux().isBlank()) {
                        String[] parts = meaning.getAux().split(",");
                        if (parts.length > 1) {
                            if (index < adjacents.size() && (adjacents.get(index).lemma().matches(parts[1]) || adjacents.get(index).tag().matches(parts[1]))) {
                                word.setTranslation(meaning.getTranslation(lang), meaning.getTransitive());
                                return;
                            }
                        }
                    }
                }
            } else {
                distance = 4;
            }
            distance++;
        }
        distance = 1;
        int distance2 = 1;
        while (distance < 10 || distance2 < 10) {
            int index = word.index() + distance;
            if (index < adjacents.size()) {
                for (Translation meaning : meanings) {
                    if (meaning.getAux() != null && !meaning.getAux().isBlank()) {
                        String[] parts = meaning.getAux().split(",");
                        if (parts.length == 3) {
                            if (index < adjacents.size() && (adjacents.get(index).lemma().matches(parts[2]) || adjacents.get(index).tag().matches(parts[2]))) {
                                word.setTranslation(meaning.getTranslation(lang), meaning.getTransitive());
                                return;
                            }
                        }
                    }
                }
            } else {
                distance = 10;
            }
            index = word.index() + distance2 * -1;
            if (index > 0) {
                for (Translation meaning : meanings) {
                    if (meaning.getAux() != null && !meaning.getAux().isBlank()) {
                        String[] parts = meaning.getAux().split(",");
                        if (parts.length == 3) {
                            if (index < adjacents.size() && (adjacents.get(index).lemma().matches(parts[2]) || adjacents.get(index).tag().matches(parts[2]))) {
                                word.setTranslation(meaning.getTranslation(lang), meaning.getTransitive());
                                return;
                            }
                        }
                    }
                }
            } else {
                distance2 = 10;
            }
            distance++;
            distance2++;
        }
    }

    private void echoTime(String label, long t1) {
        long t2 = System.nanoTime();
        double diff = (t2 - t1) / 1000000;
        System.out.println(label + " time: " + diff);
    }

    private String getTranslation(List<ExtWord> list) {
        if (list != null && !list.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (ExtWord word : list) {
                if (word.translation() != null) {
                    sb.append(word.translation());
                    if (!word.translation().equals("(") && !word.translation().equals("[")) {
                        sb.append(" ");
                    }
                }
            }
            return sb.toString();
//            return prettify(sb);
        } else {
            return "";
        }
    }

//    private String prettify(StringBuilder sb) {
//        String text = sb.toString().replaceAll("\\s+", " ");
//        for (int i = 0; i < pretty.length; i++) {
//            text = text.replace(pretty[i][0], pretty[i][1]);
//        }
//        return text;
//    }
    private List<ExtWord> joinery(List<ExtWord> in) {
        List<ExtWord> ret = new ArrayList();
        if (in == null || in.isEmpty()) {
            return ret;
        }
        in = in.stream().filter(e -> !e.isDisabled() && e.translation() != null && !e.translation().isBlank()).collect(Collectors.toList());
        for (int i = 0; i < in.size(); i++) {
            String trans = in.get(i).translation();
            if (trans.matches("[\\.,;:?!؟،؛\\)\\]]")) {
                ret.add(in.get(i));
                continue;
            } else if (trans.matches("ام|ات|اش")) {
                if (i > 0) {
                    if (in.get(i - 1).translation().endsWith("ها")) {
                        in.get(i).setTranslation(trans.replace("ا", "ی"));
                        ret.add(in.get(i));
                        continue;
                    } else if (in.get(i - 1).translation().endsWith("ه")) {
                        ret.add(halfSpace);
                        ret.add(in.get(i));
                        continue;
                    }
                } else {
                    in.get(i).setTranslation(trans.substring(1));
                    ret.add(in.get(i));
                    continue;
                }

            } else if (in.get(i).eqt("PRP$") || trans.matches("یم|یت|یش")) {
                ret.add(halfSpace);
                ret.add(in.get(i));
                continue;
            } else if (in.get(i).eqw("for") && in.get(i + 1).eqw("sake")) {
                continue;
            }
            if (i > 0 && !in.get(i - 1).matchw("[\\(\\[‘“]") && !in.get(i).matchw("[\\)\\]’”]")) {
                ret.add(space);
            }
            ret.add(in.get(i));
            if (in.get(i).tag().matches("NNS?")) {
                if (i < in.size() - 1 && in.get(i + 1).tag().startsWith("JJ") && in.get(i + 1).partOfSentence() != null && in.get(i + 1).partOfSentence().equals(in.get(i).partOfSentence())) {
                    if (trans.endsWith("ا") || trans.endsWith("و")) {
                        in.get(i).setTranslation(trans + "ی");
                    }
                }
            }
        }
        return ret;
    }

    private static boolean shouldTranslate(String w) {
        if (w.matches(CLAUSE_PLACEHOLDER)) {
            return false;
        }
        switch (w.length()) {
            case 1:
                return w.equalsIgnoreCase("a") || w.equalsIgnoreCase("I");
            case 2:
                return w.toLowerCase().matches("an|am|as|at|be|by|do|go|he|hi|if|in|is|it|me|my|no|of|on|or|so|to|tv|up|us|we") || w.matches("Mr|Dr|Sr|Jr|CD");
            default:
                return w.matches("[A-Za-z\\-]+") && !w.matches("R[abcwxyz]+") && (EnglishUtils.hasVowel(w) || w.matches("Mrs|DVD") || w.toLowerCase().matches("why|hymns?|lynx|myrrh|myths?|pygmy|pyx|rhythms?|shyly|syncs?|wry|xysts?|cwm|crwth|brrr?|pfft|(ph)?pht|psst|ssh|(tsk)?tsk"));
        }
    }

    private String verbal(String en) {
        String ret = en;
        ret = ret.toLowerCase().replaceAll("(\\w+)(a)([^e])e(\\w*)", "$1ā$3$4"); // case -> cās
        for (int i = 0; i < letters.length; i++) {
            ret = ret.replaceAll(letters[i][0], letters[i][1]);
        }
        if (en.startsWith("A") || en.startsWith("E") || en.startsWith("O")) {
            if (!ret.startsWith("ا") && !ret.startsWith("آ")) {
                ret = 'ا' + ret;
            }
        }
        if (en.endsWith("a")) {
            if (!ret.endsWith("ا")) {
                ret += 'ا';
            }
        } else if (en.endsWith("o")) {
            if (!ret.endsWith("و")) {
                ret += 'و';
            }
        }
        return ret;
    }

    protected void transform(WordGroup words) {
        int i = 0;
        while (i < words.size()) {
            Class c = transformMap.get(words.get(i).word().toLowerCase());
            if (c != null) {
                try {
                    Transformable transformable = (Transformable) c.getDeclaredConstructor().newInstance();
                    transformable.check(i, words);
                } catch (Exception ex) {
                }
            }
            i++;
        }
    }

    private void addWordToSet(Set set, ExtWord tw) {
        if (tw.translation() == null && tw.word() != null) {
            if (tw.word().length() > 1 && !"SYM".equals(tw.tag()) && !tw.isPlaceHolder()) {
                set.add(tw.word());
                set.add(tw.word().toLowerCase());
                if (!tw.word().equalsIgnoreCase(tw.lemma())) {
                    int ri = tw.lemma().indexOf("⇒");
                    if (ri != -1) {
                        set.add(tw.lemma().substring(0, ri));
                    } else {
                        set.add(tw.lemma());
                    }
                }
            }
        }
    }

    public static Map<String, Class> transformMap = new HashMap();

    static {
        Map<String, Class> mapT = new HashMap();
        mapT.put("one", One.class);
        mapT.put("ones", One.class);
        transformMap = Collections.unmodifiableMap(mapT);
    }

    private static final String quicktrans[][] = {
        {".", "."}, {"?", "؟"},
        {",", "،"}, {";", "؛"},
        {"“", "”"}, {"”", "“"},
        {"‘", "’"}, {"’", "‘"},
        //        {"-LRB-", "("}, {"-RRB-", ")"},
        {"-LSB-", "["}, {"-RSB-", "]"},
        {"-RCB-", "{"}, {"-LCB-", "}"},
        {"--", "—"}
    };

    private static final String letters[][] = {
        {"tion", "شن"}, {"sion", "شن"}, {"ture", "چر"}, {"Ice", "آیس"}, {"wicz", "ویچ"},
        {"ice", "ایس"}, {"ce", "س"}, {"ci", "سی"}, {"cy", "سی"}, {"Sp", "اسپ"}, {"St", "است"},
        {"C\\.", "سی."}, {"E\\.", "ای."}, {"I\\.", "آی."}, {"J\\.", "جی."},
        {"H\\.", "اچ."}, {"L\\.", "ال."}, {"R\\.", "آر."}, {"[C|c]h", "چ"},
        {"[S|s]h", "ش"}, {"[K|k]h", "خ"}, {"St", "است"}, {"ge", "ج"}, {"[P|p]h", "ف"},
        {"[W|w]h?", "و"},
        {"A", "آ"}, {"E", "ا"}, {"O|U", "ا"}, {"I", "ای"},
        {"Ci", "سی"}, {"ci", "سی"}, {"Mc", "مک"}, {"Th", "ت"},
        {"th", "ت"}, {"mm", "م"}, {"ss", "س"},
        {"tt", "ت"}, {"a", ""}, {"o", ""}, {"B|bb?", "ب"},
        {"C|c[ck]?", "ک"}, {"D|dd?", "د"}, {"e", ""},
        {"F|f", "ف"}, {"G|g", "گ"}, {"H|h", "ه"}, {"i", "ی"},
        {"J|j", "ج"}, {"K|k", "ک"}, {"ll", "ل"}, {"L|l", "ل"},
        {"M|mm?", "م"}, {"N|nn?", "ن"}, {"P|pp?", "پ"},
        {"Q|q", "ک"}, {"R|rr?", "ر"}, {"T|tt?", "ت"}, {"S|ss?", "س"},
        {"u", "و"}, {"V|v", "و"}, {"X", "اکس"}, {"x", "کس"}, {"Y|y", "ی"},
        {"Z|z", "ز"}, {"ā", "ی"}
    };

    public void translateFile(String filename, String domain, OntologyRepository ontologyRepository, TypeRepository typeRepository) {
        try {
            PrintWriter pw;
            try (BufferedReader reader = new BufferedReader(new FileReader("/home/afshin/Desktop/non/non.txt"))) {
                pw = new PrintWriter("/home/afshin/Desktop/non/graham2.txt", Charset.forName("UTF8"));

                String line;
                while ((line = reader.readLine()) != null) {
                    String trans = getTranslation(translateString(line, domain, ontologyRepository, typeRepository, null));
                    pw.println(trans);
                    pw.flush();
                }
            }
            pw.close();

        } catch (Exception ex) {
            System.err.println(ex.getLocalizedMessage());
        } finally {

        }

    }

    private void translateBe(ExtWord word, SovClause sov, int i) {
        int ex = sov.words().findTag("EX", 0, i - 1);
        if (ex != -1) {
            if (!word.eqwt("be/VBN")) {
                word.setTranslation(",بود,هست");
                word.setTranslations(null);
            } else {
                word.setTranslation("هستی,داشت,دارد");
                sov.get(ex).setTranslation("");
                sov.get(ex).setTranslations(null);
            }
        } else {
            word.setTranslation(",بود,است");
            word.setTranslations(null);
        }
    }

    public void setLang(String lang) {
        this.lang = lang;
    }
}
