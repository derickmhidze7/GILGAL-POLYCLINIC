UPDATE app_users SET password_hash='$2a$12$u92TyqMwp.cf5rIm9RbgS.6lFpO.Buqt7BQnb2f9aRliMbhGplIG6' WHERE username='admin';
SELECT username, LEFT(password_hash,7) as prefix, LENGTH(password_hash) as len FROM app_users WHERE username='admin';
