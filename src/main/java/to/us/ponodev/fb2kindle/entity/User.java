package to.us.ponodev.fb2kindle.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name = "users")
@Entity
public class User {

    @Id
    private long chatId;

    private String email;

    @Column(columnDefinition = "boolean default true")
    private boolean embedFonts;

    @Column(columnDefinition = "boolean default false")
    private boolean getConvertedFile;

    private String margins;

}
