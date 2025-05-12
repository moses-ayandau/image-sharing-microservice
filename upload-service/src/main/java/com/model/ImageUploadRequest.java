package upload.model;

/**
 * Image upload request model.
 */
public class ImageUploadRequest {
    private String firstName;
    private String lastName;
    private String email;
    private String image;
    private String contentType;
    private String fileExtension;

    
    
    public String getFirstName() { return firstName; }
    
    
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getImage() { return image; }
    public void setImage(String image) { this.image = image; }

    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }

    public String getFileExtension() { return fileExtension; }
    public void setFileExtension(String fileExtension) { this.fileExtension = fileExtension; }
}
