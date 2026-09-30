package fr.univ_amu.iut.importation.viewmodel;

import fr.univ_amu.iut.importation.model.PassageExistant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/// Une nuit **présente sur la carte** dont des passages sont déjà en base (#5600).
///
/// La date voyage avec les passages : sur une carte à plusieurs nuits, deux passages voisins ne disent
/// pas seuls de quelle nuit ils parlent. [PassageExistant] ne porte pas de date, et n'a pas à en
/// porter : c'est la nuit de la carte qui la donne.
///
/// @param nuit la date de la nuit, telle que la table des nuits la montre
/// @param existants les passages déjà en base pour cette nuit, jamais vide
public record NuitDejaImportee(LocalDate nuit, List<PassageExistant> existants) {

    public NuitDejaImportee {
        Objects.requireNonNull(nuit, "nuit");
        existants = List.copyOf(existants);
    }
}
