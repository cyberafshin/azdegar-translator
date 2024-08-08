/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.azdegar.translator.transform;

import com.azdegar.nlp.WordGroup;

/**
 *
 * @author Afshin Pouria
 */
public class One implements Transformable {

    @Override
    public void check(int i, WordGroup words) {
        if (words.get(i - 1).eqt("JJ")) {
            words.remove(i);
        }

    }

}
