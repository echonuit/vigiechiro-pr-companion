package fr.univ_amu.iut.commun.model;

import fr.univ_amu.iut.commun.model.dao.ReglagesDao;
import java.util.Objects;

/// Service transverse d'accès aux **réglages applicatifs** persistés (table `app_setting`, via
/// [ReglagesDao]). Offre une lecture/écriture **typée** au-dessus du couple clé/valeur en texte du
/// DAO (`boolean`, `String`, `int`) : l'appelant manipule la valeur typée, la (dé)sérialisation
/// reste ici.
///
/// Un réglage jamais écrit **retombe sur la valeur par défaut** fournie par l'appelant ; une valeur
/// stockée illisible (entier non numérique) retombe aussi sur le défaut, sans lever d'exception.
/// Aucune initialisation préalable de la base n'est nécessaire (première ouverture = tous les
/// défauts).
///
/// ## Persister un réglage énuméré : par sa valeur, jamais par son nom
///
/// Il n'y a **volontairement pas** de `lireEnum` / `ecrireEnum` ici (#2042). Une telle méthode
/// sérialiserait `Enum.name()`, c'est-à-dire l'**identifiant Java** — et renommer une constante,
/// refactoring que tout le monde tient pour sûr, ferait alors retomber silencieusement le réglage de
/// chaque utilisateur sur le défaut. Aucune erreur, aucune trace : la préférence disparaît.
///
/// L'idiome du dépôt découple la valeur persistée du nom de la constante, comme
/// [fr.univ_amu.iut.lot.model.ModeDepot] :
///
/// ```java
/// public enum ModeDepot {
///     ARCHIVES_ZIP("zip", "Archives ZIP (rapide)"),
///     SEQUENCES_WAV("wav", "Séquences WAV (audio conservé en ligne)");
///
///     /// Valeur persistée dans les réglages (stable : ne pas renommer, des bases la portent).
///     public String valeur() { … }
///
///     /// La valeur inconnue retombe sur un défaut explicite plutôt que de lever.
///     public static ModeDepot parValeur(String valeur) { … }
/// }
/// ```
///
/// Côté réglage, cela donne un [#lireTexte] suivi d'un `parValeur` — deux lignes lisibles, et le
/// renommage d'une constante n'atteint plus les bases existantes.
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
