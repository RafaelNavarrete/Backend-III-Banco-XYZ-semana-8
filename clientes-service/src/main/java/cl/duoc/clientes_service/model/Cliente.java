package cl.duoc.clientes_service.model;

public class Cliente {
    private Long id;
    private String nombre;
    private String rut;
    private String email;

    public Cliente(Long id, String nombre, String rut, String email) {
        this.id = id;
        this.nombre = nombre;
        this.rut = rut;
        this.email = email;
    }

    public Long getId() { return id; }
    public String getNombre() { return nombre; }
    public String getRut() { return rut; }
    public String getEmail() { return email; }

    public void setNombre(String nombre) { this.nombre = nombre; }
    public void setEmail(String email) { this.email = email; }
}