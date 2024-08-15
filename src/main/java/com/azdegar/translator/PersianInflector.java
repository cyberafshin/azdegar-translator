package com.azdegar.translator;

import com.azdegar.nlp.ExtWord;
import com.azdegar.nlp.SentenceType;
import com.azdegar.nlp.Person;
import com.azdegar.nlp.Voice;
import com.azdegar.nlp.Tense;
import com.azdegar.nlp.WordGroup;
import com.azdegar.translator.model.Translation;
import java.util.Map;

/**
 *
 * @author Afshin Pouria
 */
public class PersianInflector {

    public static String inflect(SovClause clause, int index) {
        ExtWord verb = clause.get(index);
        int best = 0;
        if (verb.getTranslations() != null && !verb.getTranslations().isEmpty()) {
            for (int i = 0; i < verb.getTranslations().size(); i++) {
                Translation t = (Translation) verb.getTranslations().get(i);
                if (verb.getTranslation().equals(t.getParsi())) {
                    best = i;
                }
                t.setParsi(inflect(clause, index, t));
            }
            return ((Translation) verb.getTranslations().get(best)).getParsi();
        } else {
            return inflect(clause, index, null);
        }
    }

    public static String inflect(SovClause clause, int index, Translation trans) {

        ExtWord verb = clause.get(index);
        String t = verb.translation();
        if (trans != null) {
            t = trans.getParsi();
        }
        if (!t.contains(",")) {
            if (verb.isNegative()) {
                if (verb.word().equals("is")) {
                    return "نیست";
                } else {
                    if (trans != null) {
                        return "ن" + trans.getParsi();
                    } else {
                        return "ن" + verb.translation();
                    }
                }
            } else {
                if (trans != null) {
                    return trans.getParsi();
                } else {
                    return verb.translation();
                }
            }
        }

        String[] parts = t.split(",");

        int idxGozashte = 1;
        int idxKonun = 2;
        Map<String, WordGroup> map = clause.getParts();

        if (parts.length > 4 && map != null) {
            if (map.get("iobj") != null || map.get("dobj") != null || Character.valueOf('1').equals(verb.getTransitive())) {
                idxGozashte = 3;
                idxKonun = 4;
            }
        }

        try {
            if (verb.eqt("VBN") && (clause.getTense().equals(Tense.NONE) || !verb.eql(clause.getVerb().lemma()))) {
                if (parts[0].isBlank()) { //گفت
                    return parts[idxGozashte] + "ه شده";
                } else { //جای,گرفت
                    return parts[0] + " " + parts[idxGozashte] + "ه";
                }
//                return objectName;
            }
//            if (parts[idxKonun].equals("هست") && clause.getPerson().equals(Person.THIRD_SINGULAR)) {
//                parts[idxKonun] = "است";
//            }

            if (!clause.isImperative()) {
                ExtWord modal = clause.getModal();
                if ((modal != null && modal.lemma().equals("can")) || clause.isIfStmt() || clause.isLetStmt()) {
                    if (parts[idxKonun].matches("است|هست")) {
                        parts[idxKonun] = "باش";
                    }
                }
            }

            String ret;

            if (clause.getPerson() == null || clause.getPerson() == Person.NONE) {
                clause.setPerson(Person.THIRD_PLURAL);
            }
            if (clause.getType() == SentenceType.Imperative) {
                ret = imperative(clause.getPerson(), verb.isNegative(), parts, idxKonun);
            } else if (clause.getType() == SentenceType.Subjunctive || clause.getType() == SentenceType.InterrogativeSubjunctive) {
                ret = subjunctive(clause, parts, idxGozashte, idxKonun);
            } else {
                if (clause.tense() == Tense.Present_Simple) {
                    if (clause.getModal().eql("be to")) {
                        clause.getModal().disable();
                        ret = futureSimple(clause, parts, idxGozashte);
                    } else {
                        ret = presentSimple(clause, parts, idxGozashte, idxKonun);
                    }
                } else if (clause.tense() == Tense.Present_Continuous) {
                    ret = presentContinuous(clause, parts, idxGozashte, idxKonun);
                } else if (clause.tense() == Tense.Present_Simple_Imagine) {
                    ret = presentImagine(clause, parts, idxGozashte);
                } else if (clause.tense() == Tense.Present_Perfect_Imagine) {
                    ret = perfectImagine(clause, parts, idxGozashte);
                } else if (clause.tense() == Tense.Past_Simple) {
                    if (clause.getModal().eqw("could")) {
                        ret = presentSimple(clause, parts, idxGozashte, idxKonun);
                    } else {
                        ret = pastSimple(clause, parts, idxGozashte);
                    }

                } else if (clause.tense() == Tense.Past_Continuous) {
                    ret = pastContinuous(clause, parts, idxGozashte);
                } else if (clause.getTense() == Tense.Future_Simple || clause.getTense() == Tense.Future_Continuous || clause.getTense() == Tense.Future_In_Past) {
                    ret = futureSimple(clause, parts, idxGozashte);
                } else if (clause.getTense() == Tense.Present_Perfect) {
                    ret = presentPerfect(clause, parts, idxGozashte);
                } else if (clause.getTense() == Tense.Past_Perfect) {
                    ret = pastPerfect(clause, parts, idxGozashte);
                } else if (clause.getTense() == Tense.Present_Perfect_Continuous) {
                    ret = presentPerfectContinuous(clause.getVoice(), clause.getPerson(), verb.isNegative(), parts, idxGozashte);
                } else if (clause.getTense() == Tense.Past_Perfect_Continuous) {
                    ret = pastPerfectContinuous(clause.getVoice(), clause.getPerson(), verb.isNegative(), parts, idxGozashte, idxKonun);
                } else {
                    ret = infintive(verb, clause);
                }
            }

            if (parts[parts.length - 1].contains("-") || parts[parts.length - 1].equals("را")) {
//                if (clause.isTransitive() && !clause.isPassive()) {
                verb.setPreposition(parts[parts.length - 1]);
                clause.findMovedVerb().setPreposition(verb.getPreposition());
//                    ret = parts[parts.length - 1] + " " + ret;
//                }
            }
            return ret.replace("می ", "می‌").replace("ناست", "نیست").replace("نهست", "نیست");//.replace("ن می", "نمی");
        } catch (Exception ex) {
            if (parts[0] != null && !parts[0].isBlank()) {
                return parts[0];
            } else {
                return parts[1];
            }
        }
    }

    private static String imperative(Person person, boolean negative, String[] parts, int idxKonun) {
        if (parts.length > idxKonun + 2) {
            idxKonun += 2;
        }
        String ret = negative ? parts[0].isBlank() ? "ن" : "" : "";
        if (parts[idxKonun].matches("است|هست")) {
            ret += "باش";
        } else {
            if (parts[0].isBlank()) {
                if (!parts[idxKonun].equals("باش") && !ret.endsWith("ن")) {
                    if (parts[idxKonun].startsWith("ا") || parts[idxKonun].startsWith("آ")) {
                        parts[idxKonun] = "یا" + parts[idxKonun].substring(1);
                    }
                    ret += "ب";
                }
                ret += parts[idxKonun];
            } else {
                ret += parts[0] + " ";
                if (negative && !parts[0].isBlank()) {
                    ret += "ن";
                }
                ret += parts[idxKonun];
            }
        }
        switch (person) {
            case FIRST_SINGULAR:
                ret += "م";
                break;
            case SECOND_SINGULAR:
                ret += "ی";
                break;
            case THIRD_SINGULAR:
                ret += "د";
                break;
            case FIRST_PLURAL:
                ret += "یم";
                break;
            case SECOND_PLURAL:
                ret += "ید";
                break;
            case THIRD_PLURAL:
                ret += "ند";
                break;
        }
        return ret;
    }

    private static String subjunctive(SovClause clause, String[] parts, int idxGozashte, int idxKonun) {
        Voice voice = clause.getVoice();
        Person person = clause.getPerson();
        boolean hasModal = clause.getIdxModal() != -1;
        boolean hasNeed = clause.getIdxNeed() != -1;
        int idxVerb = (hasNeed && parts[0].equals("نیاز")) ? clause.getIdxNeed() : clause.getIdxVerb();
        boolean negative = clause.get(idxVerb).isNegative();
        String ret = parts[0];
        if (!ret.isBlank()) {
            ret += " ";
        } else if (voice == Voice.Passive) {
            ret += buildObjectName(parts, idxGozashte);
        }

        ret += negative ? "ن" : "";

        if (voice == Voice.Active) {
            if (parts[idxKonun].matches("است|هست")) {
                ret += "باش";
            } else {
                if (parts[0].isBlank()) {
                    if (!parts[idxKonun].equals("باش")) {
                        if (parts[idxKonun].startsWith("ا") || parts[idxKonun].startsWith("آ")) {
                            parts[idxKonun] = "ی" + parts[idxKonun].substring(1);
                        }
                        ret += "ب";
                    }
                    ret += parts[idxKonun];
                } else {
//                    ret += parts[0] + " ";
                    if (negative && !parts[0].isBlank()) {
                        ret += "ن";
                    }
                    ret += parts[idxKonun];
                }
            }
        } else {
            if (parts[0].isBlank()) {
                if (negative) {
                    ret += "ن";
                }
                ret += "شو";
            } else {
                ret += parts[idxKonun];
            }
        }
        switch (person) {
            case FIRST_SINGULAR:
                ret += "م";
                break;
            case SECOND_SINGULAR:
                ret += "ی";
                break;
            case THIRD_SINGULAR:
                ret += "د";
                break;
            case FIRST_PLURAL:
                ret += "یم";
                break;
            case SECOND_PLURAL:
                ret += "ید";
                break;
            case THIRD_PLURAL:
                ret += "ند";
                break;
        }
        return ret;
    }

    private static String presentSimple(SovClause clause, String[] parts, int idxGozashte, int idxKonun) {
        Person person = clause.getPerson();
        boolean hasModal = clause.getIdxModal() != -1;
        boolean hasNeed = clause.getIdxNeed() != -1;
        int idxVerb = (hasNeed && parts[0].equals("نیاز")) ? clause.getIdxNeed() : clause.getIdxVerb();
        boolean negative = clause.get(idxVerb).isNegative();

        String ret = parts[0];
        if (!ret.isBlank()) {
            ret += " ";
        } else if (clause.isPassive()) {
            ret += buildObjectName(parts, idxGozashte);
        }

        ret += negative ? "ن" : "";
        if (!hasNeed) {
            if (clause.getType() == SentenceType.Subjunctive && !parts[idxKonun].matches("کن|شو|باش")) {
                ret += "ب";
            } else if (clause.isActive() && !clause.isIfStmt() && !hasModal && !parts[idxKonun].matches("است|هست|دار")) {
                ret += "می‌";
            }
        } else {

            if (!parts[idxKonun].matches("کن|شو") && !parts[0].equals("نیاز")) {
                ret += "ب";
            }
        }

        if (clause.isActive()) {
            if (ret.endsWith("می‌") && parts[idxKonun].matches("است|هست")) {
                ret += "باش";
            } else {
                if (parts[idxKonun].equals("است") && clause.get(idxVerb - 1).eqlt("there/EX")) {
                    ret += "هست";
                } else {
                    ret += parts[idxKonun];
                }
            }
        } else {
            if (parts[idxGozashte].equals("کرد") || parts[0].isBlank()) {
                if (clause.hasModal()) {
                    ret += "شو";
                } else {
                    ret += "شده ";
                    if (person != Person.THIRD_SINGULAR) {
                        ret += "ا";
                    }

                }
            } else {
                ret += parts[idxGozashte] + "ه ";
                if (person != Person.THIRD_SINGULAR) {
                    ret += "ا";
                }
            }
        }

        switch (person) {
            case FIRST_SINGULAR:
                ret += "م";
                break;
            case SECOND_SINGULAR:
                ret += "ی";
                break;
            case THIRD_SINGULAR:
                if (clause.isActive()) {
                    if (!parts[idxKonun].matches("است|هست|دارد")) {
                        ret += "د";
                    }
                } else {
                    if (clause.hasModal()) {
                        if (ret.endsWith(" شو")) {
                            ret += "د";
                        } else {
                            ret += "شود";
                        }
                    } else {
                        ret += "است";
                    }
                }
                break;
            case FIRST_PLURAL:
                ret += "یم";
                break;
            case SECOND_PLURAL:
                ret += "ید";
                break;
            case THIRD_PLURAL:
                ret += "ند";
                break;
        }
        ret = ret.replace("ناست", "نیست").replace("نهست", "نیست").replace("استیم", "ایم").replace("استید", "اید").replace("استند", "اند");
        return ret;
    }

    private static String presentImagine(SovClause clause, String[] parts, int idxGozashte) {
        Voice voice = clause.getVoice();
        Person person = clause.getPerson();
        boolean negative = clause.getVerb().isNegative();
        boolean hasModal = clause.getIdxModal() != -1;
        boolean hasNeed = clause.getIdxNeed() != -1;
        String ret = negative ? parts[0].isBlank() ? "ن" : "" : "";
        if (!parts[0].isBlank()) {
            ret += buildObjectName(parts, idxGozashte);
            if (negative) {
                ret += "‌ن";
            }
        }
        ret += "می‌";
        if (voice == Voice.Active) {
            ret += parts[idxGozashte];
        } else {
            if (parts[idxGozashte].equals("کرد") || parts[0].isBlank()) {
                ret += "شد";
            } else {
                ret += parts[idxGozashte];
            }
        }

        switch (person) {
            case FIRST_SINGULAR:
                ret += "م";
                break;
            case SECOND_SINGULAR:
                ret += "ی";
                break;
            case THIRD_SINGULAR:
                break;
            case FIRST_PLURAL:
                ret += "یم";
                break;
            case SECOND_PLURAL:
                ret += "ید";
                break;
            case THIRD_PLURAL:
                ret += "ند";
                break;
        }

        return ret;//+ "ی";
    }

    private static String perfectImagine(SovClause clause, String[] parts, int idxGozashte) {
        Voice voice = clause.getVoice();
        Person person = clause.getPerson();
        boolean negative = clause.getVerb().isNegative();
        boolean hasModal = clause.getIdxModal() != -1;
        boolean hasNeed = clause.getIdxNeed() != -1;
        String ret = negative ? parts[0].isBlank() ? "ن" : "" : "";
        if (!parts[0].isBlank()) {
            ret += buildObjectName(parts, idxGozashte);
            if (negative) {
                ret += "‌ن";
            }
        }
        if (voice == Voice.Active) {
            ret += parts[idxGozashte];
            ret += "ه بود";
        } else {
            if (parts[idxGozashte].equals("کرد") || parts[0].isBlank()) {
                ret += "شد";
            } else {
                ret += parts[idxGozashte];
            }
        }

        switch (person) {
            case FIRST_SINGULAR:
                ret += "م";
                break;
            case SECOND_SINGULAR:
                ret += "ی";
                break;
            case THIRD_SINGULAR:
//                if (!parts[idxGozashte].matches("کرد|بود")) {
                if (!ret.endsWith("بود") && !ret.endsWith("کرد") && !ret.endsWith("شد")) {
                    ret += "د";
                }
                break;
            case FIRST_PLURAL:
                ret += "یم";
                break;
            case SECOND_PLURAL:
                ret += "ید";
                break;
            case THIRD_PLURAL:
                ret += "ند";
                break;
        }

        return ret;// + "ی";
    }

    private static String presentContinuous(SovClause clause, String[] parts, int idxGozashte, int idxKonun) {
        Voice voice = clause.getVoice();
        Person person = clause.getPerson();
        boolean negative = clause.getVerb().isNegative();
        boolean hasModal = clause.getIdxModal() != -1;
        boolean hasNeed = clause.getIdxNeed() != -1;

        String ret = parts[0];
        if (ret.isBlank() && voice == Voice.Passive) {
            ret = parts[idxGozashte] + "ه";
        }

        if (!ret.isBlank()) {
            ret += " ";
        }
        if (negative) {
            ret += "ن";
        }
        if (!parts[idxKonun].matches("است|هست|دار")) {
            ret += "می‌";
        }
        if (voice == Voice.Active) {
            ret += parts[idxKonun];// + "ن";
        } else {
            if (parts[idxGozashte].equals("کرد") || parts[0].isBlank()) {
                ret += "شد";
            } else {
                ret += parts[idxGozashte] + "ه‌ا";
            }
        }
        switch (person) {
            case FIRST_SINGULAR:
                ret += "م";
                break;
            case SECOND_SINGULAR:
                ret += "ی";
                break;
            case THIRD_SINGULAR:
                ret += "د";
                break;
            case FIRST_PLURAL:
                ret += "یم";
                break;
            case SECOND_PLURAL:
                ret += "ید";
                break;
            case THIRD_PLURAL:
                ret += "ند";
                break;
        }

        return ret;
    }

    private static String pastSimple(SovClause clause, String[] parts, int idxGozashte) {
        Voice voice = clause.getVoice();
        Person person = clause.getPerson();
        boolean hasModal = clause.getIdxModal() != -1;
        boolean hasNeed = clause.getIdxNeed() != -1;
        int idxVerb = (hasNeed && parts[0].equals("نیاز")) ? clause.getIdxNeed() : clause.getIdxVerb();
        boolean negative = clause.get(idxVerb).isNegative();

        String ret = parts[0];
        if (!ret.isBlank()) {
            ret += " ";
        } else if (voice == Voice.Passive) {
            ret += buildObjectName(parts, idxGozashte);
        }
        if (!hasNeed || parts[0].equals("نیاز")) {
            ret += negative ? "ن" : "";
        }
        if (voice == Voice.Active) {
            if (clause.isIfStmt()) {
                ret += "می‌";
            }
            ret += parts[idxGozashte];
        } else {
            if (parts[idxGozashte].equals("کرد") || parts[0].isBlank()) {
                ret += "شده بود";
            } else {
                ret += parts[idxGozashte] + "ه بود";
            }
        }
        switch (person) {
            case FIRST_SINGULAR:
                ret += "م";
                break;
            case SECOND_SINGULAR:
                ret += "ی";
                break;
            case THIRD_SINGULAR:
                break;
            case FIRST_PLURAL:
                ret += "یم";
                break;
            case SECOND_PLURAL:
                ret += "ید";
                break;
            case THIRD_PLURAL:
                ret += "ند";
                break;
        }

        return ret;
    }

    private static String pastContinuous(SovClause clause, String[] parts, int idxGozashte) {
        Voice voice = clause.getVoice();
        Person person = clause.getPerson();
        boolean negative = clause.getVerb().isNegative();
        boolean hasModal = clause.getIdxModal() != -1;
        boolean hasNeed = clause.getIdxNeed() != -1;

        String ret = parts[0];
        if (ret.isBlank() && voice == Voice.Passive) {
            ret = parts[idxGozashte] + "ه";
        }
        if (!ret.isBlank()) {
            ret += " ";
        }
        if (negative) {
            ret += "ن";
        }
        ret += "می‌";
        if (voice == Voice.Active) {
            ret += parts[idxGozashte];// + "ن";
        } else {
            if (parts[idxGozashte].equals("کرد") || parts[0].isBlank()) {
                ret += "شد";
            } else {
                ret += parts[idxGozashte];// + "ن";
            }
        }
        switch (person) {
            case FIRST_SINGULAR:
                ret += "م";
                break;
            case SECOND_SINGULAR:
                ret += "ی";
                break;
            case THIRD_SINGULAR:
                ret += "";
                break;
            case FIRST_PLURAL:
                ret += "یم";
                break;
            case SECOND_PLURAL:
                ret += "ید";
                break;
            case THIRD_PLURAL:
                ret += "ند";
                break;
        }

        return ret;
    }

    private static String futureSimple(SovClause clause, String[] parts, int idxGozashte) {
        Voice voice = clause.getVoice();
        Person person = clause.getPerson();
        boolean negative = clause.getVerb().isNegative();
        boolean hasModal = clause.getIdxModal() != -1;
        boolean hasNeed = clause.getIdxNeed() != -1;

        String ret = "";
        if (voice == Voice.Passive) {
            ret = buildObjectName(parts, idxGozashte) + " ";
        } else {
            if (!parts[0].isBlank()) {
                ret = parts[0] + " ";
            }
        }

        ret += negative ? "ن" : "";

        ret += "خواه";
        switch (person) {
            case FIRST_SINGULAR:
                ret += "م";
                break;
            case SECOND_SINGULAR:
                ret += "ی";
                break;
            case THIRD_SINGULAR:
                ret += "د";
                break;
            case FIRST_PLURAL:
                ret += "یم";
                break;
            case SECOND_PLURAL:
                ret += "ید";
                break;
            case THIRD_PLURAL:
                ret += "ند";
                break;
            default:
                ret += "د";
                break;
        }
        ret += " ";
        if (voice == Voice.Active) {
//            if (parts[0].isBlank()) {
            ret += parts[idxGozashte];
//            } else {
//                ret += "کرد";
//            }
        } else {
            ret += "شد";
        }
        return ret;
    }

    private static String presentPerfect(SovClause clause, String[] parts, int idxGozashte) {
        Voice voice = clause.getVoice();
        Person person = clause.getPerson();
        boolean negative = clause.getVerb().isNegative();
        boolean hasModal = clause.getIdxModal() != -1;
        boolean hasNeed = clause.getIdxNeed() != -1;

//         ret = negative ? parts[0].isBlank() ? "ن" : "" : "";
        String ret = buildObjectName(parts, idxGozashte);
        boolean ex = clause.get(clause.getIdxVerb() - 2).eqlt("there/EX");
        if (ex) {
            ret += " داشته";
        }

        if (voice == Voice.Active) {
            if (parts[0].isBlank()) {
                if (negative) {
                    ret = "ن" + ret;
                }
            } else {
                if (negative) {
                    ret += " ن";
                }
                ret += parts[1] + "ه‌";
            }
        } else {
            if (negative) {
                ret += "ن";
            }
            if (parts[idxGozashte].equals("کرد") || parts[0].isBlank()) {
                ret += "شده ";
            } else {
                if (!ex) {
                    ret += parts[idxGozashte] + "ه ";
                    if (!parts[0].isBlank() && negative) {
                        ret += "ن";
                    }
                    ret += "شده ";
                }
            }
        }
        switch (person) {
            case FIRST_SINGULAR:
                ret += " ام";
                break;
            case SECOND_SINGULAR:
                ret += " ای";
                break;
            case THIRD_SINGULAR:
                ret += " است";
                break;
            case FIRST_PLURAL:
                ret += " ایم";
                break;
            case SECOND_PLURAL:
                ret += " اید";
                break;
            case THIRD_PLURAL:
                ret += " اند";
                break;
        }

        return ret;
    }

    private static String presentPerfectContinuous(Voice voice, Person person, boolean negative, String[] parts, int idxGozashte) {
        String ret = buildObjectName(parts, idxGozashte);
        if (voice == Voice.Active) {
            if (!parts[0].isBlank()) {
                ret += negative ? "ن" : "";
                ret += "می‌";
            } else {
                if (!negative) {
                    ret = "می‌" + ret;
                } else {
                    ret = "نمی‌" + ret;
                }
            }
        } else {
            if (!parts[0].isBlank()) {
                ret += negative ? "ن" : "";
                ret += "می‌";
            } else {
                if (!negative) {
                    ret += "می‌";
                } else {
                    ret += "نمی‌";
                }
            }
        }

        if (voice == Voice.Active) {
            if (!parts[0].isBlank()) {
                ret += "شده ";
            }
        } else {
            if (parts[idxGozashte].equals("کرد") || parts[0].isBlank()) {
                ret += "شده ";
            } else {
                ret += parts[idxGozashte];
            }
        }
        switch (person) {
            case FIRST_SINGULAR:
                ret += " ام";
                break;
            case SECOND_SINGULAR:
                ret += " ای";
                break;
            case THIRD_SINGULAR:
                ret += " است";
                break;
            case FIRST_PLURAL:
                ret += " ایم";
                break;
            case SECOND_PLURAL:
                ret += " اید";
                break;
            case THIRD_PLURAL:
                ret += " اند";
                break;
        }

        return ret;
    }

    private static String pastPerfect(SovClause clause, String[] parts, int idxGozashte) {
        Voice voice = clause.getVoice();
        Person person = clause.getPerson();
        boolean negative = clause.getVerb().isNegative();
        boolean hasModal = clause.getIdxModal() != -1;
        boolean hasNeed = clause.getIdxNeed() != -1;

        String ret = buildObjectName(parts, idxGozashte);

        if (voice == Voice.Active) {
            if (parts[0].isBlank()) {
                if (negative) {
                    ret = "ن" + ret;
                }
            } else {
                if (negative) {
                    ret += " ن";
                }
                ret += parts[1] + "ه‌";
            }
        } else {
            if (negative) {
                ret += "ن";
            }

            if (parts[idxGozashte].equals("کرد") || parts[0].isBlank()) {
                ret += "شده ";
            } else {
                ret += parts[idxGozashte] + "ه ";
                if (!parts[0].isBlank() && negative) {
                    ret += "ن";
                }
                ret += "شده ";
            }
        }
        switch (person) {
            case FIRST_SINGULAR:
                ret += " بودم";
                break;
            case SECOND_SINGULAR:
                ret += " بودی";
                break;
            case THIRD_SINGULAR:
                ret += " بود";
                break;
            case FIRST_PLURAL:
                ret += " بودیم";
                break;
            case SECOND_PLURAL:
                ret += " بودید";
                break;
            case THIRD_PLURAL:
                ret += " بودند";
                break;
        }

        return ret;
    }

    private static String pastPerfectContinuous(Voice voice, Person person, boolean negative, String[] parts, int idxGozashte, int idxKonun) {
        String ret = buildObjectName(parts, idxGozashte);
        if (voice == Voice.Active) {
            if (!parts[0].isBlank()) {
                ret += negative ? "ن" : "";
                ret += "می‌";
            } else {
                if (!negative) {
                    ret = "می‌" + ret;
                } else {
                    ret = "نمی‌" + ret;
                }
            }
        } else {
            if (!parts[0].isBlank()) {
                ret += negative ? "ن" : "";
                ret += "می‌";
            } else {
                if (!negative) {
                    ret += "می‌";
                } else {
                    ret += "نمی‌";
                }
            }
        }

        if (voice == Voice.Active) {
            if (!parts[0].isBlank()) {
                ret += "شده ";
            }
        } else {
            if (parts[idxGozashte].equals("کرد") || parts[0].isBlank()) {
                ret += "شده ";
            } else {
                ret += parts[idxGozashte];
            }
        }
//        if (parts[0].isBlank()) {
        switch (person) {
            case FIRST_SINGULAR:
                ret += "‌بودم";
                break;
            case SECOND_SINGULAR:
                ret += "‌بودی";
                break;
            case THIRD_SINGULAR:
                ret += "‌بود";
                break;
            case FIRST_PLURAL:
                ret += "‌بودیم";
                break;
            case SECOND_PLURAL:
                ret += "‌بودید";
                break;
            case THIRD_PLURAL:
                ret += "‌بودند";
                break;
        }
//        } else {
//            switch (person) {
//                case FIRST_SINGULAR:
//                    ret += "بوده‌ام";
//                    break;
//                case SECOND_SINGULAR:
//                    ret += "بوده‌ای";
//                    break;
//                case THIRD_SINGULAR:
//                    ret += "بوده‌است";
//                    break;
//                case FIRST_PLURAL:
//                    ret += "بوده‌ایم";
//                    break;
//                case SECOND_PLURAL:
//                    ret += "بوده‌اید";
//                    break;
//                case THIRD_PLURAL:
//                    ret += "بوده‌اند";
//                    break;
//            }
//
//        }

        return ret;
    }

    public static String inflectModal(SovClause clause, ExtWord word) {
        if (word.eql("may")) {
            if (clause.isInterrogative()) {
                return "می‌شود";
            } else {
                return "شاید";
            }
        }
        String ret = "";
        try {
            if (!word.translation().contains(",")) {
                if (word.isNegative()) {
                    ret += "ن" + word.translation();
                } else {
                    ret = word.translation();
                }
            } else {
                String[] parts = word.translation().split(",");
                int idxGozashte = 1;
                int idxKonun = 2;
//                Map<String, WordGroup> map = clause.getParts();
//                if (parts.length > 4) {
//                    idxGozashte = 3;
//                    idxKonun = 4;
//                }
                ret = parts[0];
                if (clause.getTense() == Tense.Present_Simple || clause.getTense() == Tense.Present_Continuous) {
                    if (word.isNegative()) {
                        ret = ret + " ن";
                    } else {
                        ret += " ";
                    }
                    if (!parts[idxKonun].equals("است") || clause.getPerson() == Person.THIRD_SINGULAR || clause.getPerson() == Person.NONE) {
                        ret += parts[idxKonun];
                    } else {
                        ret += "ا";
                    }
                } else {
                    if (word.isNegative()) {
                        ret = ret + " ن";
                    } else {
                        ret += " ";
                    }
                    ret += parts[idxGozashte];
                }
            }

//            if (!ret.endsWith("است")) {
            switch (clause.getPerson()) {
                case FIRST_SINGULAR:
                    if (word.eqw("may")) {
                        ret += "ست";
                    } else {
                        ret += "م";
                    }
                    break;
                case SECOND_SINGULAR:
                    ret += "ی";
                    break;
                case THIRD_SINGULAR:
                    if (clause.getTense().equals(Tense.Present_Simple) && !ret.endsWith("ست") && !ret.endsWith("د")) {
                        ret += "د";
                    }
                    break;
                case FIRST_PLURAL:
                    ret += "یم";
                    break;
                case SECOND_PLURAL:
                    ret += "ید";
                    break;
                case THIRD_PLURAL:
                    ret += "ند";
                    break;
                default:
                    break;
            }
//            }
        } catch (Exception ex) {
            return word.translation();
        }

        return ret.replace("می ", "می‌").replace("ناست", "نیست").replace("نهست", "نیست");//.replace("ن می", "نمی");

    }

    public static String infintive(ExtWord verb, SovClause sov) {
        String passive = "";
        if (sov.isPassive()) {
            passive += "باید ";
        }
        if (verb.getTranslations() != null && !verb.getTranslations().isEmpty()) {
            for (Object o : verb.getTranslations()) {
                ((Translation) o).setParsi(passive + infintive(((Translation) o).getParsi(), verb.isNegative()));
            }
        }
//        if(sov.getVerb().eqlt(verb.wt())){

        return passive + infintive(verb.translation(), verb.isNegative());
    }

    public static String adjective(ExtWord verb) {
        if (verb.getTranslations() != null && !verb.getTranslations().isEmpty()) {
            for (Object o : verb.getTranslations()) {
                ((Translation) o).setParsi(adjective(((Translation) o).getParsi()));
            }
        }
        return adjective(verb.translation());
    }

    private static String adjective(String trans) {
        try {
            String[] parts = trans.split(",");
            if (parts.length >= 3) {
                return parts[0] + " " + parts[1] + "ن";
            } else {
                return trans;
            }
        } catch (IndexOutOfBoundsException ex) {
            return trans;
        }
    }

    private static String infintive(String trans, boolean negative) {
        try {
            String[] parts = trans.split(",");
            if (parts.length >= 3) {
                if (negative) {
                    parts[1] = "ن" + parts[1];
                }
                return parts[0] + " " + parts[1] + "ن";
            } else {
                if (negative) {
                    trans = "ن" + trans;
                }
                return trans;
            }
        } catch (IndexOutOfBoundsException | NullPointerException ex) {
            return trans;
        }
    }

    public static String participle(ExtWord word) {
        try {
            String[] parts = word.translation().split(",");
            String ret;
            if (parts.length == 3) {
                ret = parts[0] + " " + parts[1] + "ه";
            } else {
                ret = parts[0] + "ه";
            }

            return ret;
        } catch (IndexOutOfBoundsException | NullPointerException ex) {
            return word.translation();
        }
    }

    public static String inflect(ExtWord word, Tense tense, Person person, Voice voice) {
        String ret;
        if (word.isNegative()) {
            ret = "ن" + word.translation();
        } else {
            ret = word.translation();
        }
        switch (person) {
            case NONE:
                ret += "ن";
                break;
            case FIRST_SINGULAR:
                ret += "م";
                break;
            case SECOND_SINGULAR:
                ret += "ی";
                break;
            case THIRD_SINGULAR:
                ret += "د";
                break;
            case FIRST_PLURAL:
                ret += "یم";
                break;
            case SECOND_PLURAL:
                ret += "ید";
                break;
            case THIRD_PLURAL:
                ret += "ند";
                break;
            default:
                break;
        }

        return ret;
    }

    private static String buildObjectName(String[] parts, int idxGozashte) {
        if (parts[0].isBlank()) { //گفت
            return parts[idxGozashte] + "ه ";
        } else { //جای,گرفت
            return parts[0] + " ";
        }
    }

}
