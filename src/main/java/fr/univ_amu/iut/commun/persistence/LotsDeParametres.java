package fr.univ_amu.iut.commun.persistence;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/// Découpe une collection d'identifiants en **tranches liables** par SQLite (#4251).
///
/// ## Pourquoi cette borne existe
///
/// Une lecture « par lot » remplace N requêtes par une seule `... IN (?, ?, …)`. Mais SQLite refuse
/// au-delà de quelques centaines de paramètres liés (`SQLITE_MAX_VARIABLE_NUMBER`, 999 par défaut) : un
/// observateur qui suit trois cents carrés ferait échouer l'appel là où la boucle, elle, marchait.
///
/// Le remède qui supprime un défaut de lenteur ne doit pas en introduire un de **justesse**. La lecture
/// par lot est donc découpée, et reste sûre quel que soit l'inventaire - au prix d'une requête par
/// tranche, c'est-à-dire une pour un inventaire ordinaire.
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
