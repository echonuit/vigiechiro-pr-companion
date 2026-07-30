package fr.univ_amu.iut.audit.model;

import java.util.List;
import java.util.Objects;

/// Ce que **tout le workspace** deviendrait si l'on repartait d'une base neuve (#1151) : une ligne par
/// nuit, et la question qui décide de tout : *y a-t-il de la perte ?*
///
/// L'issue le dit ainsi : *« ne demander confirmation que s'il existe au moins une nuit en “perdu” »*. Un
/// reset qui ne perd rien ne doit pas s'entourer de cérémonie ; un reset qui perd de l'audio ne doit pas
/// pouvoir se faire sans qu'on l'ait dit, **nuit par nuit**, et **avant** d'agir.
///
/// @param nuits une ligne par passage, dans l'ordre
public record BilanRecuperabilite(List<RecuperabiliteNuit> nuits) {

    public BilanRecuperabilite {
        nuits = List.copyOf(Objects.requireNonNull(nuits, "nuits"));
    }

    /// Les nuits dont l'audio ne survivrait **pas** au reset : celles qu'il faut nommer avant d'agir.
    public List<RecuperabiliteNuit> perdues() {
        return nuits.stream().filter(nuit -> nuit.source().perteDefinitive()).toList();
    }

    /// Nombre de nuits pour une source donnée.
    public long nombre(SourceAudio source) {
        return nuits.stream().filter(nuit -> nuit.source() == source).count();
    }

    /// `true` si au moins une nuit perdrait son audio : **le seul cas qui exige une confirmation**.
    public boolean perteAnnoncee() {
        return !perdues().isEmpty();
    }

    /// Résumé en une ligne : « 12 nuits : 9 depuis le disque, 1 depuis le serveur, 2 perdues. »
    public String resume() {
        return nuits.size() + " nuit(s) : " + nombre(SourceAudio.DISQUE) + " depuis le disque, "
                + nombre(SourceAudio.SERVEUR) + " depuis le serveur, " + nombre(SourceAudio.PERDU) + " perdue(s).";
    }
}
