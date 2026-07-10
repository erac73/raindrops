package io.raindrops.demo;

import io.raindrops.core.RainDropsCore;
import io.raindrops.core.RainDropsCore.RainResult;
import io.raindrops.core.Drop;

import java.security.SecureRandom;
import java.util.List;
import java.util.Scanner;

public final class RainDropsDemo {

    private static final SecureRandom RNG = new SecureRandom();

    private RainDropsDemo() {}

    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        System.out.println("=" .repeat(60));
        System.out.println("  Rain Drops — Demo interactiva (Fase 1)");
        System.out.println("=" .repeat(60));

        while (true) {
            System.out.println();
            System.out.println("Elige una opción:");
            System.out.println("  1. Fragmentar y reconstruir un texto");
            System.out.println("  2. Probar resiliencia (pérdida de nodos)");
            System.out.println("  3. Demo con Python (acelerada)");
            System.out.println("  0. Salir");
            System.out.print("> ");

            String opt = sc.nextLine().trim();
            switch (opt) {
                case "1" -> demoTexto(sc);
                case "2" -> demoResiliencia();
                case "3" -> demoPython();
                case "0" -> { System.out.println("¡Hasta luego!"); return; }
                default  -> System.out.println("Opción inválida.");
            }
        }
    }

    static void demoTexto(Scanner sc) {
        System.out.print("Escribe el texto a proteger: ");
        String input = sc.nextLine();
        if (input.isBlank()) input = "Mensaje secreto de prueba";

        byte[] data = input.getBytes();
        System.out.printf("Datos: %d bytes%n", data.length);

        int n = 7;
        int k = 3;
        System.out.printf("Fragmentando en %d gotas (umbral K=%d)...%n", n, k);

        RainResult rain = RainDropsCore.drop(data, n, k, 365);
        System.out.printf("✅ %d gotas generadas (modo: %s)%n",
            rain.getDrops().size(),
            rain.isDirectMode() ? "directo" : "híbrido");

        System.out.printf("Reconstruyendo con %d gotas...%n", k);
        byte[] recovered = RainDropsCore.reconstruct(
            rain.getDrops().subList(0, k),
            rain.getMasterKey(),
            rain.getCiphertext(),
            rain.getK(),
            rain.isDirectMode()
        );

        System.out.println("✅ Reconstrucción exitosa: '" + new String(recovered) + "'");
        System.out.println("   Coinciden: " + input.equals(new String(recovered)));
    }

    static void demoResiliencia() {
        System.out.println("\n--- Resiliencia: N=7, K=3 ---");
        byte[] data = "Este es un secreto muy importante que debe sobrevivir la pérdida de nodos"
            .getBytes();

        RainResult rain = RainDropsCore.drop(data, 7, 3, 365);
        System.out.println("Gotas creadas: " + rain.getDrops().size());

        for (int perdida = 0; perdida <= 4; perdida++) {
            int disponibles = 7 - perdida;
            try {
                List<Drop> subset = rain.getDrops().subList(0, Math.min(disponibles, 3));
                byte[] recovered = RainDropsCore.reconstruct(
                    subset, rain.getMasterKey(),
                    rain.getCiphertext(), rain.getK(), rain.isDirectMode()
                );
                boolean ok = java.util.Arrays.equals(data, recovered);
                System.out.printf("  Pérdida=%d (quedan %d): %s%n",
                    perdida, disponibles, ok ? "✅ OK" : "❌ FALLA");
            } catch (Exception e) {
                System.out.printf("  Pérdida=%d (quedan %d): ❌ %s%n",
                    perdida, disponibles, e.getMessage());
            }
        }
    }

    static void demoPython() {
        System.out.println("\n--- Demo Python (acelerada) ---");
        System.out.println("Ejecuta: python raindrops/raindrops.py");
        System.out.println("Incluye:");
        System.out.println("  - Fragmentación de credenciales (N=5, K=3)");
        System.out.println("  - Fragmentación de historial médico (N=7, K=5)");
        System.out.println("  - Simulación de nodos KeeperNode");
        System.out.println("  - Reconstrucción exitosa y fallo con K-1 gotas");
    }
}
