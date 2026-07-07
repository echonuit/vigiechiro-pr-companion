package fr.univ_amu.iut.commun.model;

import fr.univ_amu.iut.commun.model.dao.ReglagesDao;
import java.util.Objects;

/// Service transverse d'accès aux **réglages applicatifs** persistés (table `app_setting`, via
/// [ReglagesDao]). Offre une lecture **typée** au-dessus du couple clé/valeur en texte du DAO :
/// l'appelant manipule un `boolean`, la (dé)sérialisation reste ici.
///
/// Un réglage jamais écrit **retombe sur la valeur par défaut** fournie par l'appelant : aucune
/// initialisation préalable de la base n'est nécessaire (première ouverture = tous les défauts).
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
}
