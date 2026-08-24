package fr.univ_amu.iut.commun.model;

import fr.univ_amu.iut.commun.model.dao.ReglagesDao;
import java.util.Objects;

/// Service transverse d'accès aux **réglages applicatifs** persistés (table `app_setting`, via
/// [ReglagesDao]). Lecture et écriture **typées** au-dessus du couple clé/valeur en texte du DAO ;
/// un réglage jamais écrit, ou dont la valeur stockée est illisible, retombe sur le défaut fourni
/// par l'appelant, sans lever.
///
/// **Pas de `lireEnum` / `ecrireEnum`, volontairement** (#2042) : elle sérialiserait `Enum.name()`,
/// donc l'identifiant Java, et renommer une constante ferait retomber silencieusement le réglage de
/// chaque utilisateur sur le défaut. L'idiome découple la valeur persistée du nom de la constante -
/// voir [fr.univ_amu.iut.lot.model.ModeDepot], dont `parValeur` suit un [#lireTexte].
public class Reglages {

    private final ReglagesDao dao;

    public Reglages(ReglagesDao dao) {
        this.dao = Objects.requireNonNull(dao, "dao");
    }

    /// Valeur booléenne du réglage `cle`, ou `defaut` s'il n'a jamais été écrit. Toute valeur stockée
    /// autre que `"true"` (insensible à la casse) est lue comme `false` ([Boolean#parseBoolean]).
    public boolean lireBooleen(String cle, boolean defaut) {
        return dao.lire(cle).map(Boolean::parseBoolean).orElse(defaut);
    }

    /// Écrit (upsert) la valeur booléenne du réglage `cle`, sérialisée en `"true"` / `"false"`.
    public void ecrireBooleen(String cle, boolean valeur) {
        dao.ecrire(cle, Boolean.toString(valeur));
    }

    /// Valeur texte du réglage `cle`, ou `defaut` s'il n'a jamais été écrit.
    public String lireTexte(String cle, String defaut) {
        return dao.lire(cle).orElse(defaut);
    }

    /// Écrit (upsert) la valeur texte du réglage `cle`.
    public void ecrireTexte(String cle, String valeur) {
        dao.ecrire(cle, valeur);
    }

    /// Valeur entière du réglage `cle`, ou `defaut` s'il n'a jamais été écrit **ou** si la valeur
    /// stockée n'est pas un entier (tolérance : on ne propage pas de `NumberFormatException`).
    public int lireEntier(String cle, int defaut) {
        return dao.lire(cle)
                .map(valeur -> {
                    try {
                        return Integer.parseInt(valeur.trim());
                    } catch (NumberFormatException erreur) {
                        return defaut;
                    }
                })
                .orElse(defaut);
    }

    /// Écrit (upsert) la valeur entière du réglage `cle`, sérialisée en base 10.
    public void ecrireEntier(String cle, int valeur) {
        dao.ecrire(cle, Integer.toString(valeur));
    }
}
