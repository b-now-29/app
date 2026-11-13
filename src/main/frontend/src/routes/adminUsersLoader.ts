import { getAdminUsers } from "../api/admin";
import { rethrowAsRedirect } from "./adminLoader";

export async function adminUsersLoader() {
  try {
    return await getAdminUsers();
  } catch (error) {
    return rethrowAsRedirect(error);
  }
}
