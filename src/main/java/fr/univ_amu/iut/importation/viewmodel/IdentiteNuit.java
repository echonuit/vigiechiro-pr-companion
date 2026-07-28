package fr.univ_amu.iut.importation.viewmodel;

/// De quelle nuit il s'agit : l'enregistreur qui l'a captée et la date de son coucher de soleil.
///
/// C'est le couple par lequel l'application reconnaît **une même nuit** vue deux fois - une fois sur la
/// carte qu'on inspecte, une fois en base. Il sert déjà au badge « déjà importée » (#147) ; il sert
/// depuis #2580 à reconnaître une nuit **déjà récupérée de Vigie-Chiro**, qu'il faut réactiver et non
/// réimporter.
///
/// La date est portée en texte, dans la forme `AAAA-MM-JJ` que la base stocke et que
/// [fr.univ_amu.iut.importation.model.ServiceImport] attend.
public record IdentiteNuit(String numeroSerie, String dateNuit) {}
