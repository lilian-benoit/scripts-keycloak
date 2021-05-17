///usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 16+
//DEPS info.picocli:picocli:4.5.0

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "UsersCSVToKeycloakJSON", mixinStandardHelpOptions = true, version = "UsersCSVToKeycloakJSON 0.1", description = "Convertir un fichier CSV en fichier JSON pour Keycloak")
class UsersCSVToKeycloakJSON implements Callable<Integer> {

    @Option(names = { "-r", "--realmName" }, required = true, paramLabel = "ROYAUME", description = "Nom du royaume")
    String nomRoyaume; // realmName

    @Option(names = { "-f", "--file" }, required = true, paramLabel = "FICHIER", description = "Fichier CSV d'entrée")
    File fichier;

    @Option(names = { "-o",
            "--output" }, required = true, paramLabel = "FICHIER", description = "Fichier JSON de sortie")
    File sortie;

    public static String DEBUT_FICHIER_JSON = """
            {
               "enabled": true,
               "realm": "%s",
               "users": [
            """;

    public static String UTILISATEUR_ENTREE = """
                {
                    "username": "%s",
                    "email": "%s",
                    "enabled": true,
                    "firstName": "%s",
                    "lastName": "%s"
                }
        """;

    public static String FIN_FICHIER_JSON = """
               ]
            }
            """;

    public static void main(String... args) {
        int exitCode = new CommandLine(new UsersCSVToKeycloakJSON()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() throws Exception {
        // Définition de l'enregistrement
        record Utilisateur(String login, String nom, String prenom, String email) {
            Utilisateur {
                login = login.trim().toLowerCase();
                nom = nom.trim();
                prenom = prenom.trim();
                email = email.trim();
            }

            boolean isEmailValide() {
                return this.email().length() > 1;
            }
        }

        if (!fichier.exists()) {
            System.err.println("[ERROR] Le fichier '" + fichier.getName() + "' n'existe pas");
            return -1;
        } else {
            List<Utilisateur> utilisateurs = null;

            // Lecture du fichier
            Path csvFichier = Path.of(fichier.getAbsolutePath());
            try (BufferedReader breader = Files.newBufferedReader(csvFichier, StandardCharsets.UTF_8)) {
                utilisateurs = breader.lines()
                        .skip(1)
                        .map(ligne -> ligne.split(";"))
                        .filter(colonnes -> colonnes.length >= 4)
                        .map(colonnes -> {
                            String login = colonnes[0];
                            String email = colonnes[1];
                            String prenom = colonnes[2];
                            String nom = colonnes[3];
                            return new Utilisateur(login, nom, prenom, email);
                        })
                        .filter(Utilisateur::isEmailValide)
                        .collect(Collectors.toList());
            }

            // Vérification qu'il y a bien un utilisateur chargé
            if (utilisateurs == null) {
                System.err.println("[ERROR] : Aucun utilisateur n'a pu être lu.");
            } else {
                System.out.println("[INFO ] : Nb utilisateur chargé : " + utilisateurs.size());
                // Generation du fichier
                Path jsonFichier = Path.of(sortie.getAbsolutePath());
                Files.deleteIfExists(jsonFichier);
                try (BufferedWriter buffer = Files.newBufferedWriter(jsonFichier, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE)) {
                    buffer.append(String.format(DEBUT_FICHIER_JSON, nomRoyaume));
                    boolean premier = true;
                    for (Utilisateur u : utilisateurs) {
                        if (premier) {
                            premier = false;
                        } else {
                            buffer.append("        ,\n");
                        }
                        buffer.append(String.format(UTILISATEUR_ENTREE, u.login(), u.email(), u.prenom(),
                        u.nom()));
                    }
                    buffer.append(FIN_FICHIER_JSON);
                } catch (IOException e) {
                    System.err.println("[ERROR] : Une erreur s'est produite pendant l'écriture du fichier.");
                    e.printStackTrace(System.err);
                }
            }
        }
        return 0;
    }

}
