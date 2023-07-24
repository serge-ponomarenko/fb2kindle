package to.us.ponodev.fb2kindle.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import to.us.ponodev.fb2kindle.entity.User;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

}