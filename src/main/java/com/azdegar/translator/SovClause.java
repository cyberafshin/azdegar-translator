package com.azdegar.translator;

import com.azdegar.nlp.SentenceType;
import com.azdegar.nlp.Person;
import com.azdegar.nlp.Voice;
import com.azdegar.nlp.Tense;
import com.azdegar.nlp.Clause;
import com.azdegar.nlp.ExtWord;
import com.azdegar.nlp.WordGroup;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.Setter;

/**
 *
 * @author Afshin Pouria
 * @since May 20, 2016
 */
@Getter
@Setter
public final class SovClause extends Clause {

    private int idxMovedVerb;

    public SovClause(WordGroup init, boolean dependent) {
        words = init;
        tense = Tense.NONE;
        voice = Voice.Active;
        person = Person.NONE;
        if (init.size() == 1) {
            if (init.get(0).isVerb()) {
                idxVerb = 0;
                type = SentenceType.Affirmative;
                tense = Tense.Present_Simple;
                switch (init.get(0).tag()) {
                    case "VB":
                        if (dependent) {
                            type = SentenceType.Affirmative;
                        } else {
                            type = SentenceType.Imperative;
                            person = Person.SECOND_PLURAL;
                        }
                        break;
                    case "VBZ":
                        type = SentenceType.Affirmative;
                        tense = Tense.Present_Simple;
                        person = Person.THIRD_SINGULAR;
                        break;
                    case "VBD":
                        tense = Tense.Past_Simple;
                        break;
                    case "VBG":
                        tense = Tense.NONE;
                        idxVerb = -1;
                        break;
                    default:
                        break;
                }

            } else {
                if (init.get(0).tag().matches("NN|NNP")) {
                    type = SentenceType.None;
                    person = Person.THIRD_SINGULAR;
                } else if (init.get(0).tag().matches("NNS")) {
                    type = SentenceType.None;
                    person = Person.THIRD_PLURAL;
                }

            }

        } else {
            type = SentenceType.None;
            person = Person.NONE;
        }
    }

    public SovClause(Clause svo, Map<String, WordGroup> parts) {
        this(svo.getWords(), svo.getParent() != null);
        place = svo.getPlace();
        tense = svo.getTense();
        voice = svo.getVoice();
        type = svo.getType();
        this.parts = parts;
        person = svo.getPerson();
        idxVerb = svo.getIdxVerb();
        idxModal = svo.getIdxModal();
        idxNeed = svo.getIdxNeed();
//        if (svo.getOpening() != null) {
//            opening = svo.getOpening();
//            opening.setTranslation(opening.word());
//        }
//        if (svo.getClosing() != null) {
//            closing = svo.getClosing();
//            closing.setTranslation(closing.word());
//        }
    }

    public void move(int from, int to) {
        ExtWord w = words.remove(from);
        words.add(to, w);
    }

    @Override
    public String toString() {
        return words.stream().filter(e -> !e.isDisabled()).map(e -> e.word()).collect(Collectors.joining(" "));
    }

    public String infoString() {
        return words.stream().filter(e -> !e.isDisabled()).map(e -> e.info()).collect(Collectors.joining(" "));
    }

    public ExtWord findMovedVerb() {
        if (idxVerb != -1) {
            for (int i = idxVerb; i < words.size(); i++) {
                if (words.get(i).index() == words.get(idxVerb).index() && !words.get(i).isDisabled()) {
                    return words.get(i);
                }
            }
        }
        return new ExtWord();

    }

    public ExtWord getModal() {
        if (idxModal != -1) {
            return words.get(idxModal);
        } else {
            return new ExtWord();
        }
    }

    @Override
    public void setOpening(ExtWord o) {
        super.setOpening(o);
        if (o != null) {
            if (opening.eqw("‘")) {
                opening.setTranslation("’");
            } else if (opening.eqw("“")) {
                opening.setTranslation("”");
            } else {
                opening.setTranslation(o.word());
            }
        }
    }

    @Override
    public void setClosing(ExtWord c) {
        super.setClosing(c);
        if (c != null) {
            if (closing.eqw("’")) {
                closing.setTranslation("‘");
            } else if (closing.eqw("”")) {
                closing.setTranslation("“");
            } else {
                closing.setTranslation(c.word());
            }
        }
    }

    public void add(int index, ExtWord word) {
        words.add(index, word);
        if (index < idxMovedVerb) {
            idxMovedVerb++;
        }
    }

}
