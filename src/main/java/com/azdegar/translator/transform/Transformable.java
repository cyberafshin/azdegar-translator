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
@FunctionalInterface
public interface Transformable {

    void check(int index, WordGroup words);

}
