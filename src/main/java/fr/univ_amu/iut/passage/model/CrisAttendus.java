package fr.univ_amu.iut.passage.model;

import java.util.List;

/// Port : les **cris attendus** dans une séquence d'écoute, c'est-à-dire la trace acoustique de ses
/// observations (instants et fréquences médianes mesurés par Tadarida). C'est la preuve d'identité
/// la plus forte dont dispose un passage **sans empreinte** : la vérification acoustique (#1309)
/// valide l'audio réimporté **contre les observations mêmes qu'on s'apprête à y rebrancher**.
///
/// **Pourquoi un port.** Les observations appartiennent à la feature `validation`, qui dépend déjà de
/// `passage` (elle lit `SequenceDao`). Une dépendance `passage → validation` fermerait donc un
/// **cycle**, refusé par `ArchitectureTest`. La feature `passage` déclare l'interface, `validation`
/// en fournit l'implémentation (`OptionalBinder`, même inversion que `CoordonneesPoint`, #547).
///
/// **Absent = dégradé, pas cassé.** Dans les injecteurs partiels (captures, tests de module),
/// l'`Optional` reste vide : la cascade retombe alors sur la vérification **structurelle** seule
/// (nom, taille, durée réelle vs en-tête WAV), de niveau `FORTE`.
public interface CrisAttendus {

    /// Cris attendus dans la séquence `idSequence` (liste **vide** si elle ne porte aucune
    /// observation : elle n'est alors pas vérifiable acoustiquement, mais elle n'a **rien à
    /// corrompre**).
    List<CriAttendu> pour(Long idSequence);
}
