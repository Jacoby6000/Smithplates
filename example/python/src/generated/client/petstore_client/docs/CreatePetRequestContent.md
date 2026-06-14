# CreatePetRequestContent


## Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**name** | **str** |  | 
**status** | [**PetStatus**](PetStatus.md) |  | 
**species** | [**PetSpecies**](PetSpecies.md) |  | 
**category_id** | **str** |  | 
**owner_id** | **str** |  | [optional] 
**tag_count** | **float** |  | 
**tags** | **List[str]** |  | 
**attributes** | [**List[PetAttribute]**](PetAttribute.md) |  | 
**photo** | **bytearray** |  | [optional] 
**metadata** | **object** |  | [optional] 
**adopted_at** | **float** |  | [optional] 

## Example

```python
from petstore_client.models.create_pet_request_content import CreatePetRequestContent

# TODO update the JSON string below
json = "{}"
# create an instance of CreatePetRequestContent from a JSON string
create_pet_request_content_instance = CreatePetRequestContent.from_json(json)
# print the JSON string representation of the object
print(CreatePetRequestContent.to_json())

# convert the object into a dict
create_pet_request_content_dict = create_pet_request_content_instance.to_dict()
# create an instance of CreatePetRequestContent from a dict
create_pet_request_content_from_dict = CreatePetRequestContent.from_dict(create_pet_request_content_dict)
```
[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)


