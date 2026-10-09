package se.eplatform.auth.dto;

import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
    @Size(max = 100, message = "Förnamn får vara max 100 tecken")
    String firstName,

    @Size(max = 100, message = "Efternamn får vara max 100 tecken")
    String lastName,

    @Size(max = 40, message = "Telefonnummer får vara max 40 tecken")
    String phone
) {}
