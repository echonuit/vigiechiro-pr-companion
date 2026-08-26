package fr.univ_amu.iut.commun.persistence;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/// Découpe une collection d'identifiants en **tranches liables** par SQLite (#4251).
///
/// ## Pourquoi cette borne existe
///
/// Une lecture « par lot » remplace N requêtes par une seule `... IN (?, ?, …)`.
///
/// **Ce commentaire a d'abord annoncé une raison fausse.** Il disait que SQLite refuse au-delà de
/// « quelques centaines » de paramètres liés (`SQLITE_MAX_VARIABLE_NUMBER`, 999). C'est l'ancienne
/// valeur par défaut : mesuré sur le pilote embarqué ici, une requête à **cinquante mille** paramètres
/// passe sans broncher, et le découpage n'a donc **jamais** évité l'échec qu'on lui prêtait.
///
/// La raison mesurée est autre, et plus modeste : le découpage borne la **taille de la requête**.
/// Cinquante mille marqueurs font une centaine de kilo-octets de SQL à construire et à analyser à
/// chaque appel. Pour un inventaire ordinaire - quelques centaines d'identifiants - il rend **une**
/// tranche et ne change rien.
public final class LotsDeParametres {

    /// Assez large pour qu'un inventaire ordinaire tienne en **une** requête, assez bas pour rester loin
    /// de la borne de SQLite même si un appelant ajoute des paramètres à côté des identifiants.
    private static final int TAILLE = 500;

    private LotsDeParametres() {}

    /// Les tranches de `identifiants`, dans l'ordre, sans doublon. Une collection vide rend zéro tranche
    /// - l'appelant ne lance alors **aucune** requête, ce qui est le comportement voulu.
    public static List<List<Long>> decouper(Collection<Long> identifiants) {
        List<Long> distincts = identifiants.stream().distinct().toList();
        List<List<Long>> tranches = new ArrayList<>();
        for (int debut = 0; debut < distincts.size(); debut += TAILLE) {
            tranches.add(distincts.subList(debut, Math.min(debut + TAILLE, distincts.size())));
        }
        return tranches;
    }
}
