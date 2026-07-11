package fr.univ_amu.iut.commun.model;

import java.util.Objects;
import java.util.Optional;
import java.util.function.BooleanSupplier;

/// Source universelle **choisie selon la préférence** (#849) : délègue à Wikipédia FR ou à GBIF selon
/// le choix courant, relu à chaque appel (prise d'effet immédiate, sans redémarrage).
///
/// Reçoit le choix sous forme de [BooleanSupplier] (`true` → Wikipédia) plutôt qu'une dépendance directe
/// à [PreferenceSourceEspece] : la logique de sélection reste ainsi testable sans persistance. En
/// production, le supplier est `PreferenceSourceEspece::prefereWikipedia` (cf. `CommunModule`).
public final class SourceUniversellePreferee implements SourceUniverselle {

    private final BooleanSupplier prefereWikipedia;
    private final SourceUniverselle wikipedia = new LienWikipediaFr();
    private final SourceUniverselle gbif = new LienGbif();

    public SourceUniversellePreferee(BooleanSupplier prefereWikipedia) {
        this.prefereWikipedia = Objects.requireNonNull(prefereWikipedia, "prefereWikipedia");
    }

    @Override
    public Optional<String> lienPourNomLatin(String nomLatin) {
        SourceUniverselle source = prefereWikipedia.getAsBoolean() ? wikipedia : gbif;
        return source.lienPourNomLatin(nomLatin);
    }
}
