package com.azdegar.translator.model;

import com.azdegar.util.StringUtils;
import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.NamedQueries;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.io.Serializable;

/**
 *
 * @author Afshin Pouria
 */
@Entity
@Table(name = "VW_TRANS")
@NamedQueries({
    @NamedQuery(name = "Translation.select", query = "SELECT t FROM Translation t where lower(t.english) in :english"),})
public class Translation implements Serializable, Comparable<Translation> {
    
    @Id
    @Column(name = "ID")
    private long id;
    @Basic(optional = false)
    @NotNull
    @Size(min = 1, max = 255)
    @Column(name = "ENGLISH")
    private String english;
    @Size(max = 1024)
    @Column(name = "ALSO")
    private String also;
    @Column(name = "LEN")
    private Short len;
    @Size(max = 32)
    @Column(name = "POS")
    private String pos;
    @Size(max = 1024)
    @Column(name = "AUX")
    private String aux;
    @Size(max = 255)
    @Column(name = "PARSI")
    private String parsi;
    @Size(max = 255)
    @Column(name = "FARSI")
    private String farsi;
    @Size(max = 2)
    @Column(name = "LANG")
    private String lang;
    @Size(max = 255)
    @Column(name = "DOMAIN_")
    private String domain;
    @Column(name = "WEIGHT")
    private Integer weight;
    @Column(name = "WEIGHT_R")
    private Integer weightR;
    @Column(name = "MODERATE")
    private Integer moderate;
    @Column(name = "GENDER")
    private Character gender;
    @Column(name = "ONTOLOGY")
    private Character ontology;
    
    public Translation() {
    }
    
    public long getId() {
        return id;
    }
    
    public void setId(long id) {
        this.id = id;
    }
    
    public String getEnglish() {
        return english;
    }
    
    public String english() {
        return english;
    }
    
    public void setEnglish(String english) {
        this.english = english;
    }
    
    public String getAlso() {
        return also;
    }
    
    public void setAlso(String also) {
        this.also = also;
    }
    
    public Short getLen() {
        return len;
    }
    
    public void setLen(Short len) {
        this.len = len;
    }
    
    public String getPos() {
        return pos;
    }
    
    public void setPos(String pos) {
        this.pos = pos;
    }
    
    public String getAux() {
        return aux;
    }
    
    public void setAux(String aux) {
        this.aux = aux;
    }
    
    public String getParsi() {
        return parsi;
    }

    public String parsi() {
        return parsi;
    }
    
    public void setParsi(String parsi) {
        this.parsi = parsi;
    }
    
    public String getFarsi() {
        return farsi;
    }

    public String farsi() {
        return farsi;
    }
    
    public String getTranslation(String lang) {
        if (lang.equals("PA") || StringUtils.isBlank(farsi)) {
            return parsi;
        }
        if (lang.equals("PM")) {
            if (moderate != null && moderate == 2) {
                return farsi;
            } else {
                return parsi;
            }
        }
        if (lang.equals("FA")) {
            if (moderate != null && moderate == 0) {
                return parsi;
            } else {
                if (farsi.contains("،")) {
                    return farsi.split("،")[0];
                } else {
                    return farsi;
                }
            }
        }
        return parsi;
    }
    
    public void setFarsi(String farsi) {
        this.farsi = farsi;
    }
    
    public String getLang() {
        return lang;
    }
    
    public void setLang(String lang) {
        this.lang = lang;
    }
    
    public String getDomain() {
        if (domain == null) {
            return "0";
        }
        return domain;
    }
    
    public void setDomain(String domain) {
        this.domain = domain;
    }
    
    public Character getGender() {
        return gender;
    }
    
    public Character getTransitive() {
        return gender;
    }
    
    public void setGender(Character gender) {
        this.gender = gender;
    }
    
    public Integer getWeight() {
        if (weight == null) {
            return 0;
        }
        return weight;
    }
    
    public void setWeight(Integer weight) {
        this.weight = weight;
    }
    
    public Integer getWeightR() {
        if (weightR == null) {
            return 0;
        }
        return weightR;
    }
    
    public void setWeightR(Integer weightR) {
        this.weightR = weightR;
    }

    public Character getOntology() {
        return ontology;
    }
    
    public boolean equalsIgnoreLang(Translation other) {
        return english.equals(other.english) && pos.equals(other.pos) && !lang.equals(other.lang);
    }
    
    @Override
    public String toString() {
        return english + " → " + parsi;
    }
    
    @Override
    public int compareTo(Translation o) {
        if (parsi != null && o.parsi != null) {
            return parsi.compareTo(o.parsi);
        } else {
            return (int) (id - o.id);
        }
    }
    
    @Override
    public boolean equals(Object obj) {
        Translation t = (Translation) obj;
        if (parsi != null && t.parsi != null) {
            return parsi.equals(t.parsi) && pos.equals(t.pos);
        } else {
            return id == t.id;
        }
    }
    
}
